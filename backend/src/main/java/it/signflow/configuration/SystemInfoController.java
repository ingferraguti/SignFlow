package it.signflow.configuration;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemInfoController {
    private final SystemInfoProperties properties;

    public SystemInfoController(SystemInfoProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/info")
    public SystemInfoResponse info() {
        return new SystemInfoResponse("SignFlow", properties.version(), "UP");
    }
}
