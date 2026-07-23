package it.signflow.identity;

import java.util.UUID;

public record OrganizationItemResponse(
        UUID id,
        String code,
        String name,
        boolean active,
        UUID partitionId) {
}
