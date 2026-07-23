package it.signflow.technicalconfig;

import java.util.UUID;

public record FseFacilityMappingResponse(
        UUID id, String facilityCode, String facilityName, UUID companyId, String companyCode,
        String operatingUnit, String department, UUID sourceSystemId, String sourceSystemCode,
        boolean active) {
}
