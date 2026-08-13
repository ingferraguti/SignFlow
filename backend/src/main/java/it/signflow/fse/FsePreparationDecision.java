package it.signflow.fse;

import java.util.UUID;

public record FsePreparationDecision(
        UUID reportId,
        String documentTypeCode,
        boolean cdaInjectionRequired,
        String reason) {
}
