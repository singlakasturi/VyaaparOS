package com.vyapaaros.order.controller;

import com.vyapaaros.order.dto.OrderActionDecisionBody;
import com.vyapaaros.order.dto.OrderActionRequestResponse;
import com.vyapaaros.order.entity.OrderActionRequestStatus;
import com.vyapaaros.order.entity.OrderActionRequestType;
import com.vyapaaros.order.service.OrderActionRequestService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/order-desk/action-requests")
public class OrderDeskActionRequestController {
    private final OrderActionRequestService service;
    public OrderDeskActionRequestController(OrderActionRequestService service){this.service=service;}

    @GetMapping public Page<OrderActionRequestResponse> list(
        @RequestParam(required=false) OrderActionRequestStatus status,
        @RequestParam(required=false) OrderActionRequestType type,
        @RequestParam(required=false) UUID orderId,
        @RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size,
        Authentication auth) {
        return service.orderDeskRequests(auth,status,type,orderId,page,size);
    }
    @PostMapping("/{requestId}/approve") public OrderActionRequestResponse approve(@PathVariable UUID requestId,
        @Valid @RequestBody(required=false) OrderActionDecisionBody body, Authentication auth) {
        return service.approve(auth,requestId,body==null?null:body.comment());
    }
    @PostMapping("/{requestId}/reject") public OrderActionRequestResponse reject(@PathVariable UUID requestId,
        @Valid @RequestBody(required=false) OrderActionDecisionBody body, Authentication auth) {
        return service.reject(auth,requestId,body==null?null:body.comment());
    }
}
