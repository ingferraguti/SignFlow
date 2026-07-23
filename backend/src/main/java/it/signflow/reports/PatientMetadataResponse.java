package it.signflow.reports;

import java.time.LocalDate;
import java.util.UUID;

public record PatientMetadataResponse(
        UUID id, String patientIdentifier, String firstName, String lastName, String fiscalCode, LocalDate birthDate) {
}
