package com.vyapaaros.auth.service;

import com.vyapaaros.auth.entity.OtpChallenge;
import com.vyapaaros.auth.repository.OtpChallengeRepository;
import com.vyapaaros.notification.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class OtpService {

    private static final Logger logger = LoggerFactory.getLogger(OtpService.class);
    private static final int OTP_LENGTH = 6;
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration OTP_VALIDITY = Duration.ofMinutes(5);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final OtpChallengeRepository otpChallengeRepository;
    private final EmailService emailService;
    private final SecureRandom secureRandom;
    private final SecretKeySpec hashingKey;
    private final Clock clock;

    @Autowired
    public OtpService(
        OtpChallengeRepository otpChallengeRepository,
        EmailService emailService,
        @Value("${app.otp.hash-secret}") String hashingSecret
    ) {
        this(
            otpChallengeRepository,
            emailService,
            hashingSecret,
            Clock.systemUTC()
        );
    }

    OtpService(
        OtpChallengeRepository otpChallengeRepository,
        EmailService emailService,
        String hashingSecret,
        Clock clock
    ) {
        this.otpChallengeRepository = otpChallengeRepository;
        this.emailService = emailService;
        this.secureRandom = new SecureRandom();
        this.clock = clock;
        if (hashingSecret == null || hashingSecret.length() < 32) {
            throw new IllegalStateException(
                "OTP_HASH_SECRET must be configured with at least 256 bits of secret material."
            );
        }
        this.hashingKey = new SecretKeySpec(
            hashingSecret.getBytes(StandardCharsets.UTF_8),
            HMAC_ALGORITHM
        );
    }

    @Transactional
    public void requestOtp(String email) {
        String normalizedEmail = normalizeEmail(email);
        Instant now = Instant.now(clock);

        OtpChallenge latestChallenge = otpChallengeRepository
            .findFirstByEmailOrderByCreatedAtDesc(normalizedEmail)
            .orElse(null);
        if (latestChallenge != null
            && latestChallenge.getCreatedAt().plusSeconds(60).isAfter(now)) {
            throw new OtpRateLimitException();
        }

        long recentRequestCount = otpChallengeRepository
            .countByEmailAndCreatedAtGreaterThanEqual(
                normalizedEmail,
                now.minus(Duration.ofMinutes(15))
            );
        if (recentRequestCount >= 5) {
            throw new OtpRateLimitException();
        }

        List<OtpChallenge> previousChallenges =
            otpChallengeRepository.findByEmailOrderByCreatedAtDesc(normalizedEmail);
        previousChallenges.stream()
            .filter(challenge -> challenge.getConsumedAt() == null)
            .forEach(challenge -> challenge.setConsumedAt(now));
        otpChallengeRepository.saveAll(previousChallenges);

        String otp = generateOtp();
        OtpChallenge challenge = new OtpChallenge();
        challenge.setEmail(normalizedEmail);
        challenge.setCodeHash(hashOtp(otp));
        challenge.setExpiresAt(now.plus(OTP_VALIDITY));
        challenge.setAttempts(0);
        challenge.setCreatedAt(now);
        otpChallengeRepository.save(challenge);

        emailService.sendOtpEmail(normalizedEmail, otp, OTP_VALIDITY);
    }

    @Transactional
    public boolean verifyOtp(String email, String otp) {
        logger.debug("OTP verification service started.");
        String normalizedEmail = normalizeEmail(email);
        Instant now = Instant.now(clock);
        OtpChallenge challenge = otpChallengeRepository
            .findLatestActiveByEmailForUpdate(normalizedEmail)
            .orElse(null);

        if (challenge == null) {
            logger.debug("OTP verification service found no active challenge.");
            return false;
        }

        if (!challenge.getExpiresAt().isAfter(now)) {
            challenge.setConsumedAt(now);
            otpChallengeRepository.save(challenge);
            return false;
        }

        if (challenge.getAttempts() >= MAX_ATTEMPTS) {
            challenge.setConsumedAt(now);
            otpChallengeRepository.save(challenge);
            return false;
        }

        challenge.setAttempts(challenge.getAttempts() + 1);
        boolean valid = otp != null
            && otp.matches("\\d{" + OTP_LENGTH + "}")
            && MessageDigest.isEqual(
                hashOtp(otp).getBytes(StandardCharsets.US_ASCII),
                challenge.getCodeHash().getBytes(StandardCharsets.US_ASCII)
            );

        if (valid || challenge.getAttempts() >= MAX_ATTEMPTS) {
            challenge.setConsumedAt(now);
        }
        otpChallengeRepository.save(challenge);
        logger.debug("OTP verification service completed; valid={}.", valid);
        return valid;
    }

    private String generateOtp() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    private String hashOtp(String otp) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(hashingKey);
            return HexFormat.of().formatHex(mac.doFinal(otp.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash OTP.", exception);
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
