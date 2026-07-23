package it.signflow.technicalconfig;

import java.util.UUID;

public record SourceSystemResponse(
        UUID id, String code, UUID companyId, String companyCode, String description, boolean active,
        String cdaType, boolean pdfA3Conversion, boolean visibleSignature, boolean multipleSignature,
        boolean sendUnsigned, boolean createCda, boolean passthrough) {
}
