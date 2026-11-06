package com.vyapaaros.auth.service;

import com.vyapaaros.auth.entity.OtpChallenge;
import com.vyapaaros.auth.repository.OtpChallengeRepository;
import com.vyapaaros.notification.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Lock;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String EMAIL = "buyer@example.com";
    private static final String SECRET = "a".repeat(32);

    @Mock
    private OtpChallengeRepository repository;

    @Mock
    private EmailService emailService;

    private OtpService service;

    @BeforeEach
    void setUp() {
        service = new OtpService(
            repository,
            emailService,
            SECRET,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void rejectsOtpAtExactExpiryBoundary() {
        OtpChallenge challenge = challenge(NOW, NOW);
        when(repository.findLatestActiveByEmailForUpdate(EMAIL))
            .thenReturn(Optional.of(challenge));

        assertFalse(service.verifyOtp(EMAIL, "123456"));
        assertTrue(challenge.getConsumedAt().equals(NOW));
    }

    @Test
    void consumesChallengeAfterFifthFailedAttempt() {
        OtpChallenge challenge = challenge(NOW, NOW.plusSeconds(60));
        challenge.setAttempts(4);
        challenge.setCodeHash(hashForTest("111111"));
        when(repository.findLatestActiveByEmailForUpdate(EMAIL))
            .thenReturn(Optional.of(challenge));

        assertFalse(service.verifyOtp(EMAIL, "000000"));
        assertTrue(challenge.getAttempts() == 5);
        assertTrue(challenge.getConsumedAt().equals(NOW));
    }

    @Test
    void rejectsRequestDuringCooldownBeforeInvalidatingActiveChallenge() {
        OtpChallenge previous = challenge(NOW.minusSeconds(30), NOW.plusSeconds(60));
        when(repository.findFirstByEmailOrderByCreatedAtDesc(EMAIL))
            .thenReturn(Optional.of(previous));

        assertThrows(OtpRateLimitException.class, () -> service.requestOtp(EMAIL));
        verify(repository, never()).saveAll(any());
        verify(repository, never()).save(any(OtpChallenge.class));
    }

    @Test
    void rejectsRequestAfterFiveRequestsInRollingWindow() {
        when(repository.findFirstByEmailOrderByCreatedAtDesc(EMAIL))
            .thenReturn(Optional.of(challenge(NOW.minusSeconds(61), NOW)));
        when(repository.countByEmailAndCreatedAtGreaterThanEqual(
            eq(EMAIL),
            eq(NOW.minusSeconds(900))
        )).thenReturn(5L);

        assertThrows(OtpRateLimitException.class, () -> service.requestOtp(EMAIL));
        verify(repository, never()).saveAll(any());
    }

    @Test
    void returnsFalseWhenNoActiveChallengeExists() {
        when(repository.findLatestActiveByEmailForUpdate(EMAIL))
            .thenReturn(Optional.empty());

        assertFalse(service.verifyOtp(EMAIL, "123456"));
        verify(repository, never()).save(any(OtpChallenge.class));
    }

    @Test
    void repeatedVerificationCannotUseConsumedChallenge() {
        OtpChallenge challenge = challenge(NOW, NOW.plusSeconds(60));
        challenge.setCodeHash(hashForTest("123456"));
        when(repository.findLatestActiveByEmailForUpdate(EMAIL))
            .thenReturn(Optional.of(challenge), Optional.empty());

        assertTrue(service.verifyOtp(EMAIL, "123456"));
        assertFalse(service.verifyOtp(EMAIL, "123456"));
    }

    @Test
    void activeChallengeQueryUsesPessimisticWriteLock() throws NoSuchMethodException {
        Lock lock = OtpChallengeRepository.class
            .getMethod("findLatestActiveByEmailForUpdate", String.class)
            .getAnnotation(Lock.class);

        assertEquals(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE, lock.value());
    }

    private OtpChallenge challenge(Instant createdAt, Instant expiresAt) {
        OtpChallenge challenge = new OtpChallenge();
        challenge.setEmail(EMAIL);
        challenge.setCreatedAt(createdAt);
        challenge.setExpiresAt(expiresAt);
        challenge.setAttempts(0);
        challenge.setConsumedAt(null);
        return challenge;
    }

    private String hashForTest(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes()));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
