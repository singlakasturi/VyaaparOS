package com.vyapaaros.security.jwt;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;

@Configuration
public class JwtConfiguration {

    private final String signingSecret;
    private final long accessTokenExpirationSeconds;

    public JwtConfiguration(
        @Value("${app.jwt.secret}") String signingSecret,
        @Value("${app.jwt.access-token-expiry-seconds}") long accessTokenExpirationSeconds
    ) {
        this.signingSecret = signingSecret;
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
    }

    @Bean
    public SecretKey jwtSigningKey() {
        if (signingSecret.isBlank()) {
            throw new IllegalStateException(
                "JWT_SECRET must be configured with a strong Base64-encoded secret."
            );
        }

        final byte[] secretBytes;
        try {
            secretBytes = Decoders.BASE64.decode(signingSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                "JWT_SECRET must be a valid Base64-encoded secret.",
                exception
            );
        }

        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                "JWT_SECRET must decode to at least 256 bits for HMAC signing."
            );
        }

        return Keys.hmacShaKeyFor(secretBytes);
    }

    @Bean
    public long accessTokenExpirationSeconds() {
        if (accessTokenExpirationSeconds <= 0) {
            throw new IllegalStateException(
                "JWT_ACCESS_TOKEN_EXPIRY_SECONDS must be greater than zero."
            );
        }
        return accessTokenExpirationSeconds;
    }
}
