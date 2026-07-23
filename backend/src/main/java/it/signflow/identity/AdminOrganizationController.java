package it.signflow.identity;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/organization")
public class AdminOrganizationController {
    private final OrganizationRepository organizationRepository;

    public AdminOrganizationController(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    @GetMapping("/partitions")
    List<OptionResponse> partitions() {
        return organizationRepository.partitions();
    }

    @GetMapping("/companies")
    List<OptionResponse> companies() {
        return organizationRepository.companies();
    }

    @GetMapping("/roles")
    List<OptionResponse> roles() {
        return organizationRepository.roles();
    }

    @GetMapping("/groups")
    List<OptionResponse> groups() {
        return organizationRepository.groups();
    }
}
