package com.vyapaaros.auth.service;

public class OtpRateLimitException extends RuntimeException {

    public OtpRateLimitException() {
        super("Unable to issue a verification code at this time. Please try again later.");
    }
}
