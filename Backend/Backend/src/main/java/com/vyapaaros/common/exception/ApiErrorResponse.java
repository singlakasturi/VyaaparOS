package com.vyapaaros.common.exception;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ApiErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    List<String> details,
    Map<String, Object> data
) {
}
