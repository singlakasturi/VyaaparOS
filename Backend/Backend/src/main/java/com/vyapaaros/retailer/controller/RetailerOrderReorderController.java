package com.vyapaaros.retailer.controller;

import com.vyapaaros.retailer.dto.RetailerReorderResponse;
import com.vyapaaros.retailer.service.RetailerOrderReorderService;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/retailer/me/orders")
public class RetailerOrderReorderController {
    private final RetailerOrderReorderService reorders;

    public RetailerOrderReorderController(RetailerOrderReorderService reorders) { this.reorders = reorders; }

    @PostMapping("/{orderId}/reorder")
    public RetailerReorderResponse reorder(@PathVariable UUID orderId, Authentication authentication) {
        return reorders.reorder(authentication, orderId);
    }
}
