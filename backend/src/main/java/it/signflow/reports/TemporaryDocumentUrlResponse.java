package it.signflow.reports;

import java.time.OffsetDateTime;

public record TemporaryDocumentUrlResponse(String url, OffsetDateTime expiresAt) {
}
