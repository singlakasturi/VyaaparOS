package com.vyapaaros.auth.controller;

import com.vyapaaros.auth.dto.AuthResponseDto;
import com.vyapaaros.auth.dto.OtpRequestDto;
import com.vyapaaros.auth.dto.OtpRequestResponseDto;
import com.vyapaaros.auth.dto.OtpVerifyDto;
import com.vyapaaros.auth.service.OtpService;
import com.vyapaaros.security.jwt.JwtService;
import com.vyapaaros.user.entity.User;
import com.vyapaaros.user.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth/otp")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private static final String GENERIC_OTP_MESSAGE =
        "If the email is eligible, a verification code has been sent.";
    private static final String INVALID_CREDENTIALS_MESSAGE =
        "The email or verification code is invalid.";

    private final OtpService otpService;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    public AuthController(
        OtpService otpService,
        UserRepository userRepository,
        JwtService jwtService
    ) {
        this.otpService = otpService;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @PostMapping("/request")
    public ResponseEntity<OtpRequestResponseDto> requestOtp(
        @Valid @RequestBody OtpRequestDto request
    ) {
        findEligibleUser(request.email()).ifPresent(user -> otpService.requestOtp(user.getEmail()));
        return ResponseEntity.ok(new OtpRequestResponseDto(GENERIC_OTP_MESSAGE));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody OtpVerifyDto request) {
        logger.debug("OTP verification started.");
        Optional<User> eligibleUser = findEligibleUser(request.email());
        if (eligibleUser.isEmpty()) {
            logger.info("OTP verification rejected: no active user found.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(INVALID_CREDENTIALS_MESSAGE));
        }

        if (!otpService.verifyOtp(request.email(), request.otp())) {
            logger.info("OTP verification rejected by OTP service.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(INVALID_CREDENTIALS_MESSAGE));
        }

        User user = eligibleUser.get();
        logger.debug("OTP accepted; creating authentication response.");
        AuthResponseDto response = jwtService.createAuthResponse(user);
        logger.debug("Authentication response created successfully.");
        return ResponseEntity.ok(response);
    }

    private Optional<User> findEligibleUser(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
            .filter(user -> "ACTIVE".equals(user.getStatus()));
    }

    private record ErrorResponse(String message) {
    }
}
