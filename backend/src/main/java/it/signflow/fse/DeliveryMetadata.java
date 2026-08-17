package it.signflow.fse;

public record DeliveryMetadata(
        String documentIdentifier,
        String documentTypeCode,
        String facilityCode,
        String facilityName,
        String operatingUnit,
        String department,
        String sourceSystemCode,
        String sha256) {
}
