package com.vyapaaros.security.jwt;

import com.vyapaaros.auth.dto.AuthResponseDto;
import com.vyapaaros.security.entity.Role;
import com.vyapaaros.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);
    private final SecretKey signingKey;
    private final long expirationSeconds;

    public JwtService(SecretKey signingKey, long accessTokenExpirationSeconds) {
        this.signingKey = signingKey;
        this.expirationSeconds = accessTokenExpirationSeconds;
    }

    @Transactional(readOnly = true)
    public AuthResponseDto createAuthResponse(User user) {
        logger.debug("JWT authentication response creation started.");
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(expirationSeconds);
        List<String> roles = user.getRoles().stream()
            .map(Role::getName)
            .toList();
        logger.debug("JWT authentication response loaded {} role(s).", roles.size());

        String token = Jwts.builder()
            .subject(user.getId().toString())
            .claim("roles", roles)
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            .signWith(signingKey)
            .compact();

        return new AuthResponseDto(
            token,
            "Bearer",
            expirationSeconds,
            new AuthResponseDto.AuthenticatedUserDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                roles
            )
        );
    }

    public Claims parse(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
