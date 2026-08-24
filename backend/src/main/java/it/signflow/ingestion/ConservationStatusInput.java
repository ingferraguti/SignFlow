package it.signflow.ingestion;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record ConservationStatusInput(
        @NotNull ServiceConservationStatus status,
        @Size(max = 240) String remoteReference,
        @Size(max = 120) String errorCode,
        OffsetDateTime occurredAt) {
}
