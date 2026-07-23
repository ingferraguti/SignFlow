package it.signflow.identity;

import java.util.UUID;

public record OptionResponse(UUID id, String code, String name, boolean active) {
}
