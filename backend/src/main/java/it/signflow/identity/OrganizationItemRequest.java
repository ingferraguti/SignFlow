package it.signflow.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record OrganizationItemRequest(
        @NotBlank @Size(max = 60) @Pattern(regexp = "[A-Za-z0-9._-]+") String code,
        @NotBlank @Size(max = 160) String name,
        UUID partitionId,
        boolean active) {
}
