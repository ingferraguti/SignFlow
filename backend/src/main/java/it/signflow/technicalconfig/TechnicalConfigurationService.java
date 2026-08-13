package it.signflow.technicalconfig;

import it.signflow.identity.ApplicationUserRepository;
import it.signflow.identity.ApplicationUserResponse;
import it.signflow.identity.OrganizationRepository;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TechnicalConfigurationService {
    private final TechnicalConfigurationRepository repository;
    private final OrganizationRepository organizationRepository;
    private final ApplicationUserRepository userRepository;

    public TechnicalConfigurationService(TechnicalConfigurationRepository repository,
            OrganizationRepository organizationRepository, ApplicationUserRepository userRepository) {
        this.repository = repository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
    }

    public List<SourceSystemResponse> sourceSystems() { return repository.sourceSystems(); }
    public SourceSystemResponse sourceSystem(UUID id) { return repository.sourceSystem(id).orElseThrow(() -> notFound("Source system")); }

    public List<FseDocumentTypeResponse> fseDocumentTypes() { return repository.fseDocumentTypes(); }

    public List<SourceSystemFseDocumentTypeResponse> sourceSystemFseDocumentTypes(UUID sourceSystemId) {
        sourceSystem(sourceSystemId);
        return repository.sourceSystemFseDocumentTypes(sourceSystemId);
    }

    @Transactional
    public List<SourceSystemFseDocumentTypeResponse> replaceSourceSystemFseDocumentTypes(UUID sourceSystemId,
            List<SourceSystemFseDocumentTypeRequest> configurations) {
        SourceSystemResponse sourceSystem = sourceSystem(sourceSystemId);
        if (configurations == null) throw badRequest("document type configuration is required");
        if (configurations.stream().map(SourceSystemFseDocumentTypeRequest::documentTypeCode).distinct().count()
                != configurations.size()) {
            throw badRequest("document types cannot be duplicated");
        }
        for (SourceSystemFseDocumentTypeRequest configuration : configurations) {
            if (!repository.fseDocumentTypeExists(configuration.documentTypeCode())) {
                throw badRequest("unsupported FSE document type: " + configuration.documentTypeCode());
            }
            if (configuration.cdaInjectionEnabled() && (!sourceSystem.createCda() || sourceSystem.passthrough())) {
                throw badRequest("CDA injection requires createCda enabled and passthrough disabled on the source system");
            }
        }
        repository.replaceSourceSystemFseDocumentTypes(sourceSystemId, configurations);
        return repository.sourceSystemFseDocumentTypes(sourceSystemId);
    }

    @Transactional
    public SourceSystemResponse createSourceSystem(SourceSystemRequest request) {
        validateSourceSystem(request);
        return sourceSystem(repository.createSourceSystem(request));
    }

    @Transactional
    public SourceSystemResponse updateSourceSystem(UUID id, SourceSystemRequest request) {
        sourceSystem(id);
        validateSourceSystem(request);
        repository.updateSourceSystem(id, request);
        return sourceSystem(id);
    }

    @Transactional
    public void deleteSourceSystem(UUID id) { sourceSystem(id); repository.delete("source_systems", id); }

    public List<SignatureProviderResponse> signatureProviders() { return repository.signatureProviders(); }
    public SignatureProviderResponse signatureProvider(UUID id) { return repository.signatureProvider(id).orElseThrow(() -> notFound("Signature provider")); }

    @Transactional
    public SignatureProviderResponse createSignatureProvider(SignatureProviderRequest request) {
        validateProvider(request);
        return signatureProvider(repository.createSignatureProvider(request));
    }

    @Transactional
    public SignatureProviderResponse updateSignatureProvider(UUID id, SignatureProviderRequest request) {
        signatureProvider(id);
        validateProvider(request);
        repository.updateSignatureProvider(id, request);
        return signatureProvider(id);
    }

    @Transactional
    public void deleteSignatureProvider(UUID id) { signatureProvider(id); repository.delete("signature_providers", id); }

    public List<SignatureAccountResponse> signatureAccounts() { return repository.signatureAccounts(); }
    public SignatureAccountResponse signatureAccount(UUID id) { return repository.signatureAccount(id).orElseThrow(() -> notFound("Signature account")); }

    @Transactional
    public SignatureAccountResponse createSignatureAccount(SignatureAccountRequest request) {
        validateAccount(request);
        return signatureAccount(repository.createSignatureAccount(request));
    }

    @Transactional
    public SignatureAccountResponse updateSignatureAccount(UUID id, SignatureAccountRequest request) {
        signatureAccount(id);
        validateAccount(request);
        repository.updateSignatureAccount(id, request);
        return signatureAccount(id);
    }

    @Transactional
    public void deleteSignatureAccount(UUID id) { signatureAccount(id); repository.delete("signature_accounts", id); }

    public List<FseFacilityMappingResponse> fseFacilityMappings() { return repository.fseFacilityMappings(); }
    public FseFacilityMappingResponse fseFacilityMapping(UUID id) { return repository.fseFacilityMapping(id).orElseThrow(() -> notFound("FSE facility mapping")); }

    @Transactional
    public FseFacilityMappingResponse createFseFacilityMapping(FseFacilityMappingRequest request) {
        validateFseMapping(request);
        return fseFacilityMapping(repository.createFseFacilityMapping(request));
    }

    @Transactional
    public FseFacilityMappingResponse updateFseFacilityMapping(UUID id, FseFacilityMappingRequest request) {
        fseFacilityMapping(id);
        validateFseMapping(request);
        repository.updateFseFacilityMapping(id, request);
        return fseFacilityMapping(id);
    }

    @Transactional
    public void deleteFseFacilityMapping(UUID id) { fseFacilityMapping(id); repository.delete("fse_facility_mappings", id); }

    private void validateSourceSystem(SourceSystemRequest request) {
        if (!organizationRepository.companyExists(request.companyId())) {
            throw badRequest("companyId does not exist");
        }
        if (request.createCda() && request.passthrough()) {
            throw badRequest("createCda and passthrough cannot both be true");
        }
    }

    private void validateProvider(SignatureProviderRequest request) {
        if (request.baseUrl() != null && !request.baseUrl().isBlank()) {
            try {
                URI uri = URI.create(request.baseUrl());
                if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException exception) {
                throw badRequest("baseUrl must be a valid HTTP or HTTPS URL");
            }
        }
        if (request.authenticationMode() == SignatureAuthenticationMode.API_KEY_REFERENCE
                && (request.credentialReference() == null || request.credentialReference().isBlank())) {
            throw badRequest("credentialReference is required for API_KEY_REFERENCE; secrets must remain external");
        }
    }

    private void validateAccount(SignatureAccountRequest request) {
        ApplicationUserResponse user = userRepository.findById(request.applicationUserId())
                .orElseThrow(() -> badRequest("applicationUserId does not exist"));
        if (user.roles().stream().noneMatch(role -> role.code().equals("SIGNER"))) {
            throw badRequest("signature accounts can only be assigned to users with the SIGNER role");
        }
        if (repository.signatureProvider(request.signatureProviderId()).isEmpty()) {
            throw badRequest("signatureProviderId does not exist");
        }
    }

    private void validateFseMapping(FseFacilityMappingRequest request) {
        if (!organizationRepository.companyExists(request.companyId())) {
            throw badRequest("companyId does not exist");
        }
        SourceSystemResponse sourceSystem = repository.sourceSystem(request.sourceSystemId())
                .orElseThrow(() -> badRequest("sourceSystemId does not exist"));
        if (!sourceSystem.companyId().equals(request.companyId())) {
            throw badRequest("sourceSystemId does not belong to companyId");
        }
    }

    private ResponseStatusException notFound(String entity) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, entity + " not found");
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
