package it.signflow.reports;

import java.util.UUID;

public record PracticeResponse(UUID id, String practiceIdentifier, String externalReference, String description) {
}
