package com.vyapaaros.auth.dto;

import java.util.List;
import java.util.UUID;

public record AuthResponseDto(
    String accessToken,
    String tokenType,
    long expiresInSeconds,
    AuthenticatedUserDto user
) {

    public record AuthenticatedUserDto(
        UUID id,
        String email,
        String fullName,
        List<String> roles
    ) {
    }
}
