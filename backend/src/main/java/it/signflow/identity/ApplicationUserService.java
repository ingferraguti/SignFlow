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
    private final NaturalPersonRepository naturalPersonRepository;

    public ApplicationUserService(ApplicationUserRepository userRepository, OrganizationRepository organizationRepository,
            NaturalPersonRepository naturalPersonRepository) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.naturalPersonRepository = naturalPersonRepository;
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
    public ApplicationUserResponse create(ApplicationUserRequest request, String actor) {
        validateReferences(request);
        UUID personId = resolvePerson(request);
        UUID id = userRepository.create(request, personId);
        bindAuthentication(id, personId, request);
        naturalPersonRepository.recordLink(id, null, personId, null, actor);
        return get(id);
    }

    @Transactional
    public ApplicationUserResponse update(UUID id, ApplicationUserRequest request, String actor) {
        ApplicationUserResponse existing = get(id);
        validateReferences(request);
        UUID personId = resolvePerson(request);
        if (!existing.naturalPersonId().equals(personId)
                && (request.identityCorrectionReason() == null
                || request.identityCorrectionReason().trim().length() < 10)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "identityCorrectionReason of at least 10 characters is required to change natural person");
        }
        userRepository.update(id, request, personId);
        bindAuthentication(id, personId, request);
        if (!existing.naturalPersonId().equals(personId)) {
            naturalPersonRepository.recordLink(id, existing.naturalPersonId(), personId,
                    request.identityCorrectionReason().trim(), actor);
        }
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
        } else if (!organizationRepository.companyBelongsToPartition(request.companyId(), request.partitionId())) {
            errors.add("companyId does not belong to partitionId");
        }
        List<UUID> roleIds = request.roleIds() == null ? List.of() : List.copyOf(request.roleIds());
        if (!organizationRepository.rolesExist(roleIds)) {
            errors.add("one or more roleIds do not exist");
        }
        List<UUID> groupIds = request.groupIds() == null ? List.of() : List.copyOf(request.groupIds());
        if (!organizationRepository.groupsExist(groupIds)) {
            errors.add("one or more groupIds do not exist");
        } else if (!organizationRepository.groupsBelongToPartition(groupIds, request.partitionId())) {
            errors.add("one or more groupIds do not belong to partitionId");
        }
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", errors));
        }
    }

    private UUID resolvePerson(ApplicationUserRequest request) {
        String scheme = NaturalPersonRepository.normalizeScheme(request.identifierScheme());
        String sourceIdentifier = request.personalIdentifier();
        if (sourceIdentifier == null || sourceIdentifier.isBlank()) {
            sourceIdentifier = request.signerFiscalCode() == null || request.signerFiscalCode().isBlank()
                    ? request.fiscalCode() : request.signerFiscalCode();
        }
        String identifier = NaturalPersonRepository.normalizeIdentifier(sourceIdentifier, scheme);
        String country = NaturalPersonRepository.normalizeCountry(request.issuingCountry(), scheme);
        String issuer = NaturalPersonRepository.normalizeIssuer(request.identifierIssuer(), scheme);
        return naturalPersonRepository.findByIdentifier(scheme, country, issuer, identifier)
                .orElseGet(() -> naturalPersonRepository.create(request.firstName(), request.lastName(),
                        scheme, country, issuer, identifier));
    }

    private void bindAuthentication(UUID userId, UUID personId, ApplicationUserRequest request) {
        String issuer = request.authenticationIssuer() == null || request.authenticationIssuer().isBlank()
                ? "legacy://signflow" : request.authenticationIssuer().trim();
        String method = request.authenticationMethod() == null || request.authenticationMethod().isBlank()
                ? "OIDC" : request.authenticationMethod().trim().toUpperCase();
        naturalPersonRepository.bindAuthentication(userId, personId, issuer, request.oidcSubject(), method);
    }
}
