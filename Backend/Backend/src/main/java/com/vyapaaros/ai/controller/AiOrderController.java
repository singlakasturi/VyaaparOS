package com.vyapaaros.ai.controller;

import com.vyapaaros.ai.dto.*;
import com.vyapaaros.ai.service.AiOrderCaptureService;
import com.vyapaaros.order.dto.CartResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/orders")
public class AiOrderController {
    private final AiOrderCaptureService service;
    public AiOrderController(AiOrderCaptureService service) { this.service = service; }

    @PostMapping("/parse")
    public ResponseEntity<AiOrderParseResponse> parse(
        @Valid @RequestBody AiOrderParseRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(service.parse(authentication, request));
    }

    @PostMapping("/apply")
    public ResponseEntity<CartResponse> apply(
        @Valid @RequestBody AiOrderApplyRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(service.apply(authentication, request));
    }
}
