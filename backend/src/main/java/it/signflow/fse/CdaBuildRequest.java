package it.signflow.fse;

import java.util.Map;
import java.util.UUID;

public record CdaBuildRequest(UUID reportId, String documentTypeCode, Map<String, String> metadata) {
    public CdaBuildRequest {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
