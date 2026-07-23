package it.signflow.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ApplicationUserService {
    private static final int MAX_PAGE_SIZE = 100;

    private final ApplicationUserRepository userRepository;
    private final OrganizationRepository organizationRepository;

    public ApplicationUserService(ApplicationUserRepository userRepository, OrganizationRepository organizationRepository) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
    }

    public PageResponse<ApplicationUserResponse> search(String query, Boolean active, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return userRepository.search(query, active, safePage, safeSize);
    }

    public ApplicationUserResponse get(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application user not found"));
    }

    @Transactional
    public ApplicationUserResponse create(ApplicationUserRequest request) {
        validateReferences(request);
        UUID id = userRepository.create(request);
        return get(id);
    }

    @Transactional
    public ApplicationUserResponse update(UUID id, ApplicationUserRequest request) {
        get(id);
        validateReferences(request);
        userRepository.update(id, request);
        return get(id);
    }

    @Transactional
    public ApplicationUserResponse setActive(UUID id, boolean active) {
        get(id);
        userRepository.setActive(id, active);
        return get(id);
    }

    private void validateReferences(ApplicationUserRequest request) {
        List<String> errors = new ArrayList<>();
        if (!organizationRepository.partitionExists(request.partitionId())) {
            errors.add("partitionId does not exist");
        }
        if (!organizationRepository.companyExists(request.companyId())) {
            errors.add("companyId does not exist");
        }
        List<UUID> roleIds = request.roleIds() == null ? List.of() : List.copyOf(request.roleIds());
        if (!organizationRepository.rolesExist(roleIds)) {
            errors.add("one or more roleIds do not exist");
        }
        List<UUID> groupIds = request.groupIds() == null ? List.of() : List.copyOf(request.groupIds());
        if (!organizationRepository.groupsExist(groupIds)) {
            errors.add("one or more groupIds do not exist");
        }
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", errors));
        }
    }
}
