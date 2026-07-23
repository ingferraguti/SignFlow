package it.signflow.identity;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @GetMapping("/manage/{type}")
    List<OrganizationItemResponse> manage(@PathVariable String type) {
        return organizationRepository.organizationItems(type);
    }

    @PostMapping("/manage/{type}")
    OrganizationItemResponse create(@PathVariable String type, @Valid @RequestBody OrganizationItemRequest request) {
        return organizationRepository.save(type, UUID.randomUUID(), request);
    }

    @PutMapping("/manage/{type}/{id}")
    OrganizationItemResponse update(@PathVariable String type, @PathVariable UUID id,
            @Valid @RequestBody OrganizationItemRequest request) {
        if (!organizationRepository.organizationItems(type).stream().anyMatch(item -> item.id().equals(id))) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        }
        return organizationRepository.save(type, id, request);
    }

    @PostMapping("/manage/{type}/{id}/{action:activate|deactivate}")
    OrganizationItemResponse changeActive(@PathVariable String type, @PathVariable UUID id, @PathVariable String action) {
        return organizationRepository.setActive(type, id, action.equals("activate"));
    }
}
