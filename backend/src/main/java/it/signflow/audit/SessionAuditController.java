package it.signflow.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/session-audit")
public class SessionAuditController {
    private final AuditService service;

    public SessionAuditController(AuditService service) {
        this.service = service;
    }

    @PostMapping("/login")
    void login(Authentication authentication, HttpServletRequest request) {
        record("LOGIN", authentication, request);
    }

    @PostMapping("/logout")
    void logout(Authentication authentication, HttpServletRequest request) {
        record("LOGOUT", authentication, request);
    }

    private void record(String type, Authentication authentication, HttpServletRequest request) {
        Object attribute = request.getAttribute(AuditWebInterceptor.CORRELATION_ATTRIBUTE);
        String correlationId = attribute == null ? UUID.randomUUID().toString() : attribute.toString();
        service.record(new AuditRecordCommand(type, "USER", authentication.getName(), correlationId,
                "SESSION", authentication.getName(), "SUCCESS", Map.of("channel", "WEB"), null));
    }
}
