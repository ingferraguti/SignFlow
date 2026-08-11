package it.signflow.identity;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;

public final class JwtRoleExtractor {
    private JwtRoleExtractor() {
    }

    public static List<String> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) {
            return List.of();
        }
        Object roles = realmAccess.get("roles");
        if (!(roles instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(role -> role.equals("ADMINISTRATOR") || role.equals("SIGNER") || role.equals("APPROVER"))
                .sorted()
                .toList();
    }
}
