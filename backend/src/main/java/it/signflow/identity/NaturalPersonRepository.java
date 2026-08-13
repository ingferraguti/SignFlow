package it.signflow.identity;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class NaturalPersonRepository {
    private final JdbcClient jdbc;

    public NaturalPersonRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UUID> findByIdentifier(String scheme, String country, String issuer, String value) {
        return jdbc.sql("""
                select natural_person_id from natural_person_identifiers
                where scheme=:scheme and issuing_country=:country and issuer=:issuer
                  and normalized_value=:value and active=true
                """).param("scheme", scheme).param("country", country).param("issuer", issuer)
                .param("value", value).query(UUID.class).optional();
    }

    public UUID create(String firstName, String lastName, String scheme, String country, String issuer, String value) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into natural_persons (id,first_name,last_name) values (:id,:firstName,:lastName)")
                .param("id", id).param("firstName", firstName.trim()).param("lastName", lastName.trim()).update();
        jdbc.sql("""
                insert into natural_person_identifiers
                    (id,natural_person_id,scheme,issuing_country,issuer,normalized_value,verified,verified_at)
                values (:id,:personId,:scheme,:country,:issuer,:value,true,now())
                """).param("id", UUID.randomUUID()).param("personId", id).param("scheme", scheme)
                .param("country", country).param("issuer", issuer).param("value", value).update();
        return id;
    }

    public Optional<UUID> personForUser(UUID userId) {
        return jdbc.sql("select natural_person_id from application_users where id=:id")
                .param("id", userId).query(UUID.class).optional();
    }

    public void bindAuthentication(UUID userId, UUID personId, String issuer, String subject, String method) {
        jdbc.sql("""
                insert into authentication_identities
                    (id,application_user_id,natural_person_id,issuer,subject,authentication_method)
                values (:id,:userId,:personId,:issuer,:subject,:method)
                on conflict (issuer,subject) do update set
                    application_user_id=excluded.application_user_id,
                    natural_person_id=excluded.natural_person_id,
                    authentication_method=excluded.authentication_method,
                    active=true
                """).param("id", UUID.randomUUID()).param("userId", userId).param("personId", personId)
                .param("issuer", issuer).param("subject", subject).param("method", method).update();
    }

    public void recordLink(UUID userId, UUID previous, UUID resulting, String reason, String actor) {
        String type = previous == null || previous.equals(resulting) ? "PROFILE_LINKED" : "IDENTITY_CORRECTED";
        jdbc.sql("""
                insert into natural_person_identity_events
                    (id,application_user_id,previous_natural_person_id,resulting_natural_person_id,
                     event_type,reason,actor)
                values (:id,:userId,:previous,:resulting,:type,:reason,:actor)
                """).param("id", UUID.randomUUID()).param("userId", userId).param("previous", previous)
                .param("resulting", resulting).param("type", type).param("reason", reason)
                .param("actor", actor == null || actor.isBlank() ? "system" : actor.trim()).update();
    }

    public static String normalizeScheme(String value) {
        String scheme = text(value, "IT_TAX_CODE").toUpperCase(Locale.ROOT);
        if (!scheme.equals("IT_TAX_CODE") && !scheme.equals("EIDAS_PERSON_IDENTIFIER")
                && !scheme.equals("NATIONAL_ID")) throw new IllegalArgumentException("Unsupported identifier scheme");
        return scheme;
    }

    public static String normalizeCountry(String value, String scheme) {
        String country = text(value, scheme.equals("IT_TAX_CODE") ? "IT" : null);
        if (country == null || !country.toUpperCase(Locale.ROOT).matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("Issuing country must be a two-letter ISO code");
        }
        return country.toUpperCase(Locale.ROOT);
    }

    public static String normalizeIdentifier(String value, String scheme) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Personal identifier is required");
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        if (scheme.equals("IT_TAX_CODE") && normalized.startsWith("TINIT-")) normalized = normalized.substring(6);
        if (scheme.equals("IT_TAX_CODE") && !normalized.matches("[A-Z0-9]{16}")) {
            throw new IllegalArgumentException("Italian tax code must contain 16 letters or digits");
        }
        return normalized;
    }

    public static String normalizeIssuer(String value, String scheme) {
        return text(value, scheme.equals("IT_TAX_CODE") ? "AGENZIA_ENTRATE" : null);
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
