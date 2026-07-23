package it.signflow.technicalconfig;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record FseFacilityMappingRequest(
        @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Za-z0-9._-]+") String facilityCode,
        @NotBlank @Size(max = 200) String facilityName,
        @NotNull UUID companyId,
        @NotBlank @Size(max = 160) String operatingUnit,
        @NotBlank @Size(max = 160) String department,
        @NotNull UUID sourceSystemId,
        boolean active) {
}
