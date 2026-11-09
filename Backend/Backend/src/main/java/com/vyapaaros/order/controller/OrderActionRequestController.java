package com.vyapaaros.order.controller;

import com.vyapaaros.order.dto.OrderActionRequestBody;
import com.vyapaaros.order.dto.ReturnRequestBody;
import com.vyapaaros.order.dto.OrderActionRequestResponse;
import com.vyapaaros.order.service.OrderActionRequestService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders/{orderId}/action-requests")
public class OrderActionRequestController {
    private final OrderActionRequestService service;
    public OrderActionRequestController(OrderActionRequestService service){this.service=service;}

    @GetMapping public List<OrderActionRequestResponse> list(@PathVariable UUID orderId, Authentication auth) {
        return service.retailerRequests(auth,orderId);
    }
    @PostMapping("/cancellation")
    public ResponseEntity<OrderActionRequestResponse> cancellation(@PathVariable UUID orderId,
        @Valid @RequestBody(required=false) OrderActionRequestBody body, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.requestCancellation(auth,orderId,body==null?null:body.reason()));
    }
    @PostMapping("/return")
    public ResponseEntity<OrderActionRequestResponse> returns(@PathVariable UUID orderId,
        @Valid @RequestBody ReturnRequestBody body, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.requestReturn(auth,orderId,body.reason()));
    }
}
