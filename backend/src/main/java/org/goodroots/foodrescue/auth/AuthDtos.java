package org.goodroots.foodrescue.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.goodroots.foodrescue.user.UserRole;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
        @NotBlank @Size(max = 120) String fullName,
        @Size(max = 160) String organizationName,
        @NotBlank @Email @Size(max = 190) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotNull UserRole role
    ) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record AuthResponse(String token, long userId, String fullName, String organizationName, String email,
                               UserRole role) {}
}
