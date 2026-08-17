package it.signflow.ingestion;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Composite;
import ca.uhn.hl7v2.model.Group;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Primitive;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Structure;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.Varies;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
final class Hl7V2Parser {
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Rome");
    private final HapiContext context;

    Hl7V2Parser(HapiContext context) {
        this.context = context;
    }

    ParsedHl7Message parse(String raw) {
        if (raw == null || raw.isBlank()) throw invalid("EMPTY_MESSAGE", "HL7 message is empty");
        try {
            Message message = context.getPipeParser().parse(normalize(raw));
            List<Segment> segments = new ArrayList<>();
            collect(message, segments);
            Segment msh = required(segments, "MSH");
            String type = upper(value(msh, 9, 1));
            String trigger = upper(value(msh, 9, 2));
            if (!"ORU".equals(type) && !"MDM".equals(type)) {
                throw invalid("UNSUPPORTED_MESSAGE_TYPE", "Only ORU and MDM messages are supported");
            }
            Segment pid = first(segments, "PID");
            Segment obr = first(segments, "OBR");
            Segment txa = first(segments, "TXA");
            Segment pv1 = first(segments, "PV1");
            Segment signer = first(segments, "ZSF");
            DocumentPart document = document(segments, value(msh, 10, 1));
            return new ParsedHl7Message(
                    text(value(msh, 3, 1)), type, trigger, text(value(msh, 12, 1)),
                    text(value(msh, 10, 1)), value(pid, 3, 1), value(pid, 5, 2), value(pid, 5, 1),
                    value(pid, 19, 1), date(value(pid, 7, 1)), value(pv1, 19, 1),
                    "MDM".equals(type) ? value(txa, 12, 1) : value(obr, 3, 1),
                    upper("MDM".equals(type) ? value(txa, 2, 1) : value(obr, 4, 1)),
                    "MDM".equals(type) ? firstNonBlank(value(pv1, 10, 1), value(txa, 17, 1)) : value(obr, 24, 1),
                    timestamp("MDM".equals(type) ? value(txa, 4, 1)
                            : firstNonBlank(value(obr, 22, 1), value(obr, 7, 1))),
                    firstNonBlank(value(signer, 1, 1), "MDM".equals(type) ? value(txa, 10, 1) : null),
                    document.content(), document.filename());
        } catch (Hl7ParsingException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("INVALID_HL7", "HL7 v2 parsing or initial validation failed");
        }
    }

    private String normalize(String raw) {
        return raw.replace("\r\n", "\r").replace('\n', '\r').trim() + "\r";
    }

    private void collect(Group group, List<Segment> target) throws HL7Exception {
        for (String name : group.getNames()) {
            for (Structure structure : group.getAll(name)) {
                if (structure instanceof Segment segment) target.add(segment);
                else if (structure instanceof Group nested) collect(nested, target);
            }
        }
    }

    private Segment required(List<Segment> segments, String name) {
        Segment value = first(segments, name);
        if (value == null) throw invalid("MISSING_" + name, name + " segment is required");
        return value;
    }

    private Segment first(List<Segment> segments, String name) {
        return segments.stream().filter(segment -> name.equals(segment.getName())).findFirst().orElse(null);
    }

    private String value(Segment segment, int field, int component) {
        if (segment == null) return null;
        try {
            Type[] repetitions = segment.getField(field);
            if (repetitions.length == 0) return null;
            return text(primitive(component(repetitions[0], component)));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Type component(Type field, int number) {
        if (field instanceof Varies varies) return component(varies.getData(), number);
        if (number <= 1 && field instanceof Primitive) return field;
        if (!(field instanceof Composite composite)) return number == 1 ? field : null;
        Type[] components = composite.getComponents();
        if (number < 1 || number > components.length) return null;
        return components[number - 1];
    }

    private String primitive(Type type) throws HL7Exception {
        if (type == null) return null;
        if (type instanceof Primitive primitive) return primitive.getValue();
        if (type instanceof Composite composite) {
            for (Type component : composite.getComponents()) {
                String result = primitive(component);
                if (result != null && !result.isBlank()) return result;
            }
        }
        return type.encode();
    }

    private DocumentPart document(List<Segment> segments, String controlId) {
        for (Segment obx : segments.stream().filter(segment -> "OBX".equals(segment.getName())).toList()) {
            if (!"ED".equalsIgnoreCase(value(obx, 2, 1))) continue;
            String encoding = value(obx, 5, 4);
            String data = value(obx, 5, 5);
            if (data == null || !"BASE64".equalsIgnoreCase(encoding)) continue;
            try {
                return new DocumentPart(Base64.getDecoder().decode(data.getBytes(StandardCharsets.US_ASCII)),
                        safeFilename(value(obx, 3, 2), controlId));
            } catch (IllegalArgumentException exception) {
                throw invalid("INVALID_DOCUMENT_ENCODING", "Embedded document is not valid Base64");
            }
        }
        return new DocumentPart(null, safeFilename(null, controlId));
    }

    private String safeFilename(String value, String controlId) {
        String base = text(value);
        if (base == null || !base.toLowerCase(Locale.ROOT).endsWith(".pdf")
                || base.contains("/") || base.contains("\\")) {
            base = "referto-hl7-" + safeToken(controlId) + ".pdf";
        }
        return base.length() > 255 ? base.substring(0, 251) + ".pdf" : base;
    }

    private String safeToken(String value) {
        String token = text(value);
        if (token == null) return "senza-id";
        token = token.replaceAll("[^A-Za-z0-9_-]", "-");
        return token.substring(0, Math.min(token.length(), 60));
    }

    private LocalDate date(String value) {
        String normalized = text(value);
        if (normalized == null || normalized.length() < 8) return null;
        try { return LocalDate.parse(normalized.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE); }
        catch (DateTimeParseException ignored) { return null; }
    }

    private OffsetDateTime timestamp(String value) {
        String normalized = text(value);
        if (normalized == null || normalized.length() < 8) return null;
        try {
            if (normalized.length() >= 14) {
                return LocalDateTime.parse(normalized.substring(0, 14), DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                        .atZone(DEFAULT_ZONE).toOffsetDateTime();
            }
            if (normalized.length() >= 12) {
                return LocalDateTime.parse(normalized.substring(0, 12), DateTimeFormatter.ofPattern("yyyyMMddHHmm"))
                        .atZone(DEFAULT_ZONE).toOffsetDateTime();
            }
            return LocalDate.parse(normalized.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE)
                    .atStartOfDay(DEFAULT_ZONE).toOffsetDateTime();
        } catch (DateTimeParseException ignored) { return null; }
    }

    private String firstNonBlank(String first, String second) { return text(first) == null ? text(second) : text(first); }
    private String upper(String value) { String result = text(value); return result == null ? null : result.toUpperCase(Locale.ROOT); }
    private String text(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private Hl7ParsingException invalid(String code, String message) { return new Hl7ParsingException(code, message); }
    private record DocumentPart(byte[] content, String filename) {}
}
