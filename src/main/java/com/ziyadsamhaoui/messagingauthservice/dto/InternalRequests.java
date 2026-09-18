package com.ziyadsamhaoui.messagingauthservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class InternalRequests {

    private InternalRequests() {
    }

    public record CreateUserRequest(
            @NotNull UUID id,
            @NotBlank @Size(min = 3, max = 50)
            @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "must be alphanumeric with . _ - only")
            String username
    ) {}
}
