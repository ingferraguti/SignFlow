package it.signflow.technicalconfig;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/technical-config")
public class AdminTechnicalConfigurationController {
    private final TechnicalConfigurationService service;

    public AdminTechnicalConfigurationController(TechnicalConfigurationService service) { this.service = service; }

    @GetMapping("/source-systems") List<SourceSystemResponse> sourceSystems() { return service.sourceSystems(); }
    @GetMapping("/source-systems/{id}") SourceSystemResponse sourceSystem(@PathVariable UUID id) { return service.sourceSystem(id); }
    @PostMapping("/source-systems") SourceSystemResponse createSourceSystem(@Valid @RequestBody SourceSystemRequest request) { return service.createSourceSystem(request); }
    @PutMapping("/source-systems/{id}") SourceSystemResponse updateSourceSystem(@PathVariable UUID id, @Valid @RequestBody SourceSystemRequest request) { return service.updateSourceSystem(id, request); }
    @DeleteMapping("/source-systems/{id}") ResponseEntity<Void> deleteSourceSystem(@PathVariable UUID id) { service.deleteSourceSystem(id); return ResponseEntity.noContent().build(); }

    @GetMapping("/signature-providers") List<SignatureProviderResponse> signatureProviders() { return service.signatureProviders(); }
    @GetMapping("/signature-providers/{id}") SignatureProviderResponse signatureProvider(@PathVariable UUID id) { return service.signatureProvider(id); }
    @PostMapping("/signature-providers") SignatureProviderResponse createSignatureProvider(@Valid @RequestBody SignatureProviderRequest request) { return service.createSignatureProvider(request); }
    @PutMapping("/signature-providers/{id}") SignatureProviderResponse updateSignatureProvider(@PathVariable UUID id, @Valid @RequestBody SignatureProviderRequest request) { return service.updateSignatureProvider(id, request); }
    @DeleteMapping("/signature-providers/{id}") ResponseEntity<Void> deleteSignatureProvider(@PathVariable UUID id) { service.deleteSignatureProvider(id); return ResponseEntity.noContent().build(); }

    @GetMapping("/signature-accounts") List<SignatureAccountResponse> signatureAccounts() { return service.signatureAccounts(); }
    @GetMapping("/signature-accounts/{id}") SignatureAccountResponse signatureAccount(@PathVariable UUID id) { return service.signatureAccount(id); }
    @PostMapping("/signature-accounts") SignatureAccountResponse createSignatureAccount(@Valid @RequestBody SignatureAccountRequest request) { return service.createSignatureAccount(request); }
    @PutMapping("/signature-accounts/{id}") SignatureAccountResponse updateSignatureAccount(@PathVariable UUID id, @Valid @RequestBody SignatureAccountRequest request) { return service.updateSignatureAccount(id, request); }
    @DeleteMapping("/signature-accounts/{id}") ResponseEntity<Void> deleteSignatureAccount(@PathVariable UUID id) { service.deleteSignatureAccount(id); return ResponseEntity.noContent().build(); }

    @GetMapping("/fse-facility-mappings") List<FseFacilityMappingResponse> fseFacilityMappings() { return service.fseFacilityMappings(); }
    @GetMapping("/fse-facility-mappings/{id}") FseFacilityMappingResponse fseFacilityMapping(@PathVariable UUID id) { return service.fseFacilityMapping(id); }
    @PostMapping("/fse-facility-mappings") FseFacilityMappingResponse createFseFacilityMapping(@Valid @RequestBody FseFacilityMappingRequest request) { return service.createFseFacilityMapping(request); }
    @PutMapping("/fse-facility-mappings/{id}") FseFacilityMappingResponse updateFseFacilityMapping(@PathVariable UUID id, @Valid @RequestBody FseFacilityMappingRequest request) { return service.updateFseFacilityMapping(id, request); }
    @DeleteMapping("/fse-facility-mappings/{id}") ResponseEntity<Void> deleteFseFacilityMapping(@PathVariable UUID id) { service.deleteFseFacilityMapping(id); return ResponseEntity.noContent().build(); }
}
