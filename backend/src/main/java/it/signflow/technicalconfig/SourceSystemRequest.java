package it.signflow.technicalconfig;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SourceSystemRequest(
        @NotBlank @Size(max = 60) @Pattern(regexp = "[A-Za-z0-9._-]+") String code,
        @NotNull UUID companyId,
        @NotBlank @Size(max = 300) String description,
        boolean active,
        @NotBlank @Size(max = 60) String cdaType,
        boolean pdfA3Conversion,
        boolean visibleSignature,
        boolean multipleSignature,
        boolean sendUnsigned,
        boolean createCda,
        boolean passthrough) {
}
