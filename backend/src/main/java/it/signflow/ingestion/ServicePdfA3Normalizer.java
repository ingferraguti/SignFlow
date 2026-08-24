package it.signflow.ingestion;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.schema.PDFAIdentificationSchema;
import org.apache.xmpbox.xml.XmpSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.PDFAParser;
import org.verapdf.pdfa.PDFAValidator;
import org.verapdf.pdfa.flavours.PDFAFlavour;

/** Converts supported, unencrypted source documents into raster-safe PDF/A-3B and validates the result. */
@Component
public class ServicePdfA3Normalizer {
    static final String PROFILE = "PDF/A-3B";
    static final String VALIDATOR = "veraPDF 1.30.2";
    private static final Set<String> PDF_TYPES = Set.of("application/pdf", "application/x-pdf",
            "application/acrobat", "application/vnd.pdf", "applications/vnd.pdf", "text/pdf");
    private static final Set<String> PNG_TYPES = Set.of("image/png", "image/x-png");
    private static final Set<String> JPEG_TYPES = Set.of("image/jpeg", "image/jpg", "image/pjpeg");
    private static final Set<String> TIFF_TYPES = Set.of("image/tiff", "image/tif", "image/x-tiff");
    private static final Set<String> TEXT_TYPES = Set.of("text/plain");
    private static final Set<String> GENERIC_TYPES = Set.of("application/octet-stream", "binary/octet-stream");

    static {
        VeraGreenfieldFoundryProvider.initialise();
    }

    private final int dpi;
    private final int maxPages;
    private final long maxRenderedPixels;

    ServicePdfA3Normalizer(
            @Value("${signflow.signature-requests.pdfa3-render-dpi:144}") int dpi,
            @Value("${signflow.signature-requests.max-pages:100}") int maxPages,
            @Value("${signflow.signature-requests.max-rendered-pixels:100000000}") long maxRenderedPixels) {
        this.dpi = Math.max(96, Math.min(300, dpi));
        this.maxPages = Math.max(1, Math.min(1000, maxPages));
        this.maxRenderedPixels = Math.max(1_000_000L, maxRenderedPixels);
    }

    NormalizedDocument normalize(byte[] source, String declaredContentType, String filename, long maxOutputSize) {
        if (source == null || source.length == 0) throw badRequest("A non-empty document is required");
        SourceFormat detected = detect(source);
        String declared = baseMediaType(declaredContentType);
        if (declared != null && !GENERIC_TYPES.contains(declared) && !matches(declared, detected)) {
            throw badRequest("Declared content type does not match the document content");
        }
        if (detected == null) throw unsupported("Unsupported document content");
        try {
            List<PageImage> pages = switch (detected) {
                case PDF -> pdfPages(source);
                case PNG, JPEG, TIFF -> imagePages(source);
                case TEXT -> textPages(source);
            };
            if (pages.isEmpty()) throw badRequest("The source document has no pages");
            if (pages.size() > maxPages) throw unprocessable("Document exceeds the maximum of " + maxPages + " pages");
            byte[] converted = createPdfA3(pages, filename);
            if (converted.length > maxOutputSize) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                        "Normalized PDF/A-3 exceeds the configured limit of " + maxOutputSize + " bytes");
            }
            validatePdfA3(converted);
            return new NormalizedDocument(converted, detected.canonicalMediaType, sha256(converted),
                    PROFILE, "3", "B", VALIDATOR, pages.size());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unprocessable("Document cannot be transformed into a valid PDF/A-3B", exception);
        }
    }

    private List<PageImage> pdfPages(byte[] source) throws Exception {
        try (PDDocument document = Loader.loadPDF(source)) {
            if (document.isEncrypted()) throw unprocessable("Encrypted PDFs are not supported");
            if (!document.getSignatureDictionaries().isEmpty()) {
                throw unprocessable("Already signed PDFs cannot be normalized for a new signature request");
            }
            int count = document.getNumberOfPages();
            if (count == 0) throw badRequest("The PDF has no pages");
            if (count > maxPages) throw unprocessable("Document exceeds the maximum of " + maxPages + " pages");
            PDFRenderer renderer = new PDFRenderer(document);
            List<PageImage> pages = new ArrayList<>(count);
            long pixels = 0;
            for (int index = 0; index < count; index++) {
                PDRectangle box = document.getPage(index).getCropBox();
                long width = Math.max(1, Math.round(box.getWidth() * dpi / 72d));
                long height = Math.max(1, Math.round(box.getHeight() * dpi / 72d));
                pixels = Math.addExact(pixels, Math.multiplyExact(width, height));
                if (pixels > maxRenderedPixels) throw unprocessable("Document exceeds the rendered-pixel safety limit");
                BufferedImage image = opaqueRgb(renderer.renderImageWithDPI(index, dpi, ImageType.RGB));
                pages.add(new PageImage(image, box.getWidth(), box.getHeight()));
            }
            return pages;
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException exception) {
            throw unprocessable("Encrypted or password-protected PDFs are not supported");
        }
    }

    private List<PageImage> imagePages(byte[] source) throws Exception {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            if (input == null) throw badRequest("Invalid image content");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw unsupported("Unsupported image encoding");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, true);
                int count;
                try { count = reader.getNumImages(true); }
                catch (UnsupportedOperationException exception) { count = 1; }
                if (count > maxPages) throw unprocessable("Document exceeds the maximum of " + maxPages + " pages");
                List<PageImage> pages = new ArrayList<>(count);
                long pixels = 0;
                for (int index = 0; index < count; index++) {
                    BufferedImage image = opaqueRgb(reader.read(index));
                    pixels = Math.addExact(pixels, Math.multiplyExact((long) image.getWidth(), image.getHeight()));
                    if (pixels > maxRenderedPixels) throw unprocessable("Document exceeds the rendered-pixel safety limit");
                    pages.add(fittedA4(image));
                }
                return pages;
            } finally {
                reader.dispose();
            }
        }
    }

    private List<PageImage> textPages(byte[] source) throws Exception {
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(source)).toString();
        if (text.indexOf('\0') >= 0) throw badRequest("Plain text contains binary data");
        int width = 1240;
        int height = 1754;
        int margin = 90;
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 24);
        BufferedImage measure = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D mg = measure.createGraphics();
        mg.setFont(font);
        FontMetrics metrics = mg.getFontMetrics();
        int lineHeight = metrics.getHeight();
        int maxWidth = width - margin * 2;
        List<String> lines = wrapText(text, metrics, maxWidth);
        mg.dispose();
        int linesPerPage = Math.max(1, (height - margin * 2) / lineHeight);
        int pageCount = Math.max(1, (lines.size() + linesPerPage - 1) / linesPerPage);
        if (pageCount > maxPages) throw unprocessable("Document exceeds the maximum of " + maxPages + " pages");
        if ((long) width * height * pageCount > maxRenderedPixels) {
            throw unprocessable("Document exceeds the rendered-pixel safety limit");
        }
        List<PageImage> pages = new ArrayList<>(pageCount);
        for (int page = 0; page < pageCount; page++) {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.BLACK); graphics.setFont(font);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int y = margin + metrics.getAscent();
            int from = page * linesPerPage;
            int to = Math.min(lines.size(), from + linesPerPage);
            for (int index = from; index < to; index++) {
                graphics.drawString(lines.get(index), margin, y); y += lineHeight;
            }
            graphics.dispose();
            pages.add(new PageImage(image, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight()));
        }
        return pages;
    }

    private List<String> wrapText(String value, FontMetrics metrics, int maxWidth) {
        List<String> result = new ArrayList<>();
        for (String paragraph : value.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            if (paragraph.isEmpty()) { result.add(""); continue; }
            String remaining = paragraph;
            while (!remaining.isEmpty()) {
                int end = remaining.length();
                while (end > 1 && metrics.stringWidth(remaining.substring(0, end)) > maxWidth) end--;
                if (end < remaining.length()) {
                    int space = remaining.lastIndexOf(' ', end - 1);
                    if (space > 0) end = space;
                }
                result.add(remaining.substring(0, end));
                remaining = remaining.substring(end).stripLeading();
            }
        }
        return result;
    }

    private byte[] createPdfA3(List<PageImage> pages, String filename) throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.setVersion(1.7f);
            PDDocumentCatalog catalog = document.getDocumentCatalog();
            catalog.setLanguage("it-IT");
            addMetadata(document, catalog, filename);
            addOutputIntent(document, catalog);
            for (PageImage value : pages) {
                PDPage page = new PDPage(new PDRectangle(value.widthPoints, value.heightPoints));
                document.addPage(page);
                PDImageXObject image = JPEGFactory.createFromImage(document, value.image, 0.92f, dpi);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.drawImage(image, 0, 0, value.widthPoints, value.heightPoints);
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private void addMetadata(PDDocument document, PDDocumentCatalog catalog, String filename) throws Exception {
        XMPMetadata xmp = XMPMetadata.createXMPMetadata();
        PDFAIdentificationSchema identification = xmp.createAndAddPDFAIdentificationSchema();
        identification.setPart(3);
        identification.setConformance("B");
        DublinCoreSchema dc = xmp.createAndAddDublinCoreSchema();
        dc.setTitle(filename == null || filename.isBlank() ? "SignFlow normalized document" : filename);
        ByteArrayOutputStream serialized = new ByteArrayOutputStream();
        new XmpSerializer().serialize(xmp, serialized, true);
        PDMetadata metadata = new PDMetadata(document);
        metadata.importXMPMetadata(serialized.toByteArray());
        catalog.setMetadata(metadata);
    }

    private void addOutputIntent(PDDocument document, PDDocumentCatalog catalog) throws Exception {
        byte[] profile = ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData();
        PDOutputIntent intent = new PDOutputIntent(document, new ByteArrayInputStream(profile));
        intent.setInfo("sRGB IEC61966-2.1");
        intent.setOutputCondition("sRGB IEC61966-2.1");
        intent.setOutputConditionIdentifier("sRGB IEC61966-2.1");
        intent.setRegistryName("http://www.color.org");
        catalog.addOutputIntent(intent);
    }

    private void validatePdfA3(byte[] pdf) throws Exception {
        var foundry = Foundries.defaultInstance();
        try (PDFAParser parser = foundry.createParser(new ByteArrayInputStream(pdf), PDFAFlavour.PDFA_3_B);
             PDFAValidator validator = foundry.createValidator(PDFAFlavour.PDFA_3_B, false)) {
            var result = validator.validate(parser);
            if (!result.isCompliant()) {
                String failed = result.getFailedChecks().entrySet().stream().limit(5)
                        .map(entry -> entry.getKey() + "=" + entry.getValue()).reduce((a, b) -> a + ", " + b).orElse("unknown");
                throw new IllegalStateException("veraPDF rejected PDF/A-3B: " + failed);
            }
        }
    }

    private SourceFormat detect(byte[] content) {
        if (indexOf(content, "%PDF-".getBytes(StandardCharsets.US_ASCII), 1024) >= 0) return SourceFormat.PDF;
        if (startsWith(content, new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10})) return SourceFormat.PNG;
        if (startsWith(content, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff})) return SourceFormat.JPEG;
        if (startsWith(content, new byte[]{'I', 'I', 42, 0}) || startsWith(content, new byte[]{'M', 'M', 0, 42})) return SourceFormat.TIFF;
        String declaredText = new String(content, 0, Math.min(content.length, 4096), StandardCharsets.UTF_8);
        if (declaredText.indexOf('\0') < 0 && declaredText.chars().filter(ch -> Character.isISOControl(ch)
                && ch != '\r' && ch != '\n' && ch != '\t').count() == 0) return SourceFormat.TEXT;
        return null;
    }

    private boolean matches(String declared, SourceFormat format) {
        if (format == null) return false;
        return switch (format) {
            case PDF -> PDF_TYPES.contains(declared);
            case PNG -> PNG_TYPES.contains(declared);
            case JPEG -> JPEG_TYPES.contains(declared);
            case TIFF -> TIFF_TYPES.contains(declared);
            case TEXT -> TEXT_TYPES.contains(declared);
        };
    }

    private String baseMediaType(String value) {
        if (value == null || value.isBlank()) return null;
        return value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private PageImage fittedA4(BufferedImage image) {
        float pageWidth = image.getWidth() > image.getHeight() ? PDRectangle.A4.getHeight() : PDRectangle.A4.getWidth();
        float pageHeight = image.getWidth() > image.getHeight() ? PDRectangle.A4.getWidth() : PDRectangle.A4.getHeight();
        return new PageImage(image, pageWidth, pageHeight);
    }

    private BufferedImage opaqueRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) return source;
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, result.getWidth(), result.getHeight());
        graphics.drawImage(source, 0, 0, null); graphics.dispose();
        return result;
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) if (value[index] != prefix[index]) return false;
        return true;
    }

    private int indexOf(byte[] value, byte[] target, int limit) {
        int end = Math.min(value.length - target.length, limit);
        outer: for (int index = 0; index <= end; index++) {
            for (int offset = 0; offset < target.length; offset++) if (value[index + offset] != target[offset]) continue outer;
            return index;
        }
        return -1;
    }

    private String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    private ResponseStatusException unsupported(String message) {
        return new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message);
    }
    private ResponseStatusException unprocessable(String message) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
    private ResponseStatusException unprocessable(String message, Exception cause) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message, cause);
    }

    record NormalizedDocument(byte[] content, String sourceContentType, String sha256, String profile,
                              String pdfaPart, String pdfaConformance, String validator, int pageCount) {
        NormalizedDocument { content = content.clone(); }
        @Override public byte[] content() { return content.clone(); }
    }
    private record PageImage(BufferedImage image, float widthPoints, float heightPoints) {}
    private enum SourceFormat {
        PDF("application/pdf"), PNG("image/png"), JPEG("image/jpeg"), TIFF("image/tiff"), TEXT("text/plain");
        private final String canonicalMediaType;
        SourceFormat(String canonicalMediaType) { this.canonicalMediaType = canonicalMediaType; }
    }
}
