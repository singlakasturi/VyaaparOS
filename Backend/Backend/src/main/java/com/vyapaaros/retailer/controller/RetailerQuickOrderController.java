package com.vyapaaros.retailer.controller;

import com.vyapaaros.retailer.dto.QuickOrderRequest;
import com.vyapaaros.retailer.dto.QuickOrderResponse;
import com.vyapaaros.retailer.service.RetailerQuickOrderService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/retailer/me/quick-order")
public class RetailerQuickOrderController {
    private final RetailerQuickOrderService quickOrders;

    public RetailerQuickOrderController(RetailerQuickOrderService quickOrders) { this.quickOrders = quickOrders; }

    @PostMapping
    public ResponseEntity<QuickOrderResponse> addRows(@RequestParam UUID retailerId,
        @Valid @RequestBody QuickOrderRequest request, Authentication authentication) {
        return ResponseEntity.ok(quickOrders.addRows(authentication, retailerId, request));
    }
}
