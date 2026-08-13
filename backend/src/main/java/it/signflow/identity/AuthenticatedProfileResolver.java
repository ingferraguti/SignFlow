package it.signflow.identity;

import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthenticatedProfileResolver {
    private final JdbcClient jdbc;

    public AuthenticatedProfileResolver(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public String username(Jwt jwt) {
        if (jwt == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
        String subject = jwt.getSubject();
        if (issuer != null && subject != null) {
            Optional<String> exact = jdbc.sql("""
                    select u.username from authentication_identities ai
                    join application_users u on u.id=ai.application_user_id
                    join natural_persons np on np.id=ai.natural_person_id
                    where ai.issuer=:issuer and ai.subject=:subject and ai.active=true
                      and u.active=true and np.active=true
                    """).param("issuer", issuer).param("subject", subject).query(String.class).optional();
            if (exact.isPresent()) return exact.get();
        }
        String preferred = jwt.getClaimAsString("preferred_username");
        if (preferred == null || preferred.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication identity is not linked");
        }
        return jdbc.sql("""
                select u.username from application_users u
                join natural_persons np on np.id=u.natural_person_id
                where u.username=:username and u.active=true and np.active=true
                """).param("username", preferred).query(String.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Authentication identity is not linked"));
    }
}
