package it.signflow.identity;

import java.util.List;

public record CurrentUserResponse(String username, String name, String email, List<String> roles) {
}
