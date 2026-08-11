package it.signflow.audit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuditWebInterceptor implements HandlerInterceptor {
    static final String CORRELATION_ATTRIBUTE = "signflow.audit.correlationId";
    private static final String EVENT_ATTRIBUTE = "signflow.audit.eventType";
    private static final Pattern DOCUMENT_ID = Pattern.compile("/documents/([0-9a-fA-F-]{36})");
    private final ObjectProvider<AuditService> serviceProvider;

    AuditWebInterceptor(ObjectProvider<AuditService> serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String supplied = request.getHeader("X-Correlation-ID");
        String correlationId = supplied != null && supplied.matches("[A-Za-z0-9._:-]{1,160}")
                ? supplied : UUID.randomUUID().toString();
        request.setAttribute(CORRELATION_ATTRIBUTE, correlationId);
        response.setHeader("X-Correlation-ID", correlationId);
        String event = classify(request);
        if (event != null) request.setAttribute(EVENT_ATTRIBUTE, event);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        Object classified = request.getAttribute(EVENT_ATTRIBUTE);
        if (classified == null) return;
        AuditService service = serviceProvider.getIfAvailable();
        if (service == null) return;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor = actor(authentication);
        String path = request.getRequestURI();
        String entityType = classified.toString().equals("DOCUMENT_OPENED") || classified.toString().equals("DOCUMENT_UPLOAD_REQUESTED")
                ? "DOCUMENT" : classified.toString().equals("CONFIGURATION_CHANGED") ? "CONFIGURATION" : "ADMIN_SEARCH";
        String entityId = extractEntityId(path, entityType);
        String outcome = response.getStatus() >= 400 ? (response.getStatus() == 403 ? "DENIED" : "FAILURE") : "SUCCESS";
        try {
            service.record(new AuditRecordCommand(classified.toString(), "USER", actor,
                    request.getAttribute(CORRELATION_ATTRIBUTE).toString(), entityType, entityId, outcome,
                    Map.of("httpMethod", request.getMethod(), "route", safeRoute(path),
                            "filterCount", request.getParameterMap().size(), "statusCode", response.getStatus()),
                    null));
        } catch (RuntimeException ignored) {
            // Audit failures must not replace the business response; database-backed operations remain audited by triggers.
        }
    }

    private String classify(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (path.startsWith("/api/admin/audit") || path.startsWith("/api/session-audit")) return null;
        if (method.equals("GET") && (path.equals("/api/admin/reports") || path.equals("/api/admin/users")))
            return "ADMIN_SENSITIVE_SEARCH";
        if ((method.equals("GET") || method.equals("POST"))
                && (path.contains("/content") || path.contains("temporary-url") || path.contains("/preview")))
            return "DOCUMENT_OPENED";
        if (method.equals("POST") && path.matches("/api/admin/reports/[0-9a-fA-F-]{36}/documents"))
            return "DOCUMENT_UPLOAD_REQUESTED";
        if (!method.equals("GET") && !method.equals("OPTIONS")
                && (path.startsWith("/api/admin/technical-config") || path.startsWith("/api/admin/organization")
                    || path.startsWith("/api/admin/users") || path.startsWith("/api/admin/ui-texts")))
            return "CONFIGURATION_CHANGED";
        return null;
    }

    private String actor(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String username = jwt.getToken().getClaimAsString("preferred_username");
            if (username != null && !username.isBlank()) return username;
        }
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) return "anonymous";
        return authentication.getName();
    }

    private String extractEntityId(String path, String type) {
        if (type.equals("DOCUMENT")) {
            Matcher matcher = DOCUMENT_ID.matcher(path);
            if (matcher.find()) return matcher.group(1);
            Matcher report = Pattern.compile("/reports/([0-9a-fA-F-]{36})").matcher(path);
            if (report.find()) return "report:" + report.group(1);
        }
        return type.equals("CONFIGURATION") ? safeRoute(path) : "administration";
    }

    private String safeRoute(String path) {
        return path.replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F-]{27}", "{id}");
    }
}
