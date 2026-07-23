package it.signflow.identity;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class AdminApplicationUserController {
    private final ApplicationUserService applicationUserService;

    public AdminApplicationUserController(ApplicationUserService applicationUserService) {
        this.applicationUserService = applicationUserService;
    }

    @GetMapping
    PageResponse<ApplicationUserResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return applicationUserService.search(query, active, page, size);
    }

    @PostMapping
    ApplicationUserResponse create(@Valid @RequestBody ApplicationUserRequest request) {
        return applicationUserService.create(request);
    }

    @GetMapping("/{id}")
    ApplicationUserResponse get(@PathVariable UUID id) {
        return applicationUserService.get(id);
    }

    @PutMapping("/{id}")
    ApplicationUserResponse update(@PathVariable UUID id, @Valid @RequestBody ApplicationUserRequest request) {
        return applicationUserService.update(id, request);
    }

    @PostMapping("/{id}/activate")
    ApplicationUserResponse activate(@PathVariable UUID id) {
        return applicationUserService.setActive(id, true);
    }

    @PostMapping("/{id}/deactivate")
    ApplicationUserResponse deactivate(@PathVariable UUID id) {
        return applicationUserService.setActive(id, false);
    }
}
