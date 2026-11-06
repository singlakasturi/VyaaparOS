package com.vyapaaros.order.controller;

import com.vyapaaros.order.dto.*;
import com.vyapaaros.order.entity.OrderStatus;
import com.vyapaaros.order.service.OrderDeskService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/order-desk")
public class OrderDeskController {
    private final OrderDeskService service;
    public OrderDeskController(OrderDeskService service) { this.service = service; }

    @GetMapping("/orders")
    public Page<OrderDeskOrderResponse> list(@RequestParam(required=false) OrderStatus status,
        @RequestParam(required=false) UUID retailerId, @RequestParam(required=false) Instant startDate,
        @RequestParam(required=false) Instant endDate, @RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size, Authentication auth) {
        return service.list(auth, status, retailerId, startDate, endDate,
            PageRequest.of(Math.max(0,page), Math.min(Math.max(1,size),100), Sort.by("createdAt").descending().and(Sort.by("id"))));
    }

    @GetMapping("/orders/{id}")
    public OrderDeskOrderResponse get(@PathVariable UUID id, Authentication auth) { return service.get(auth, id); }

    @PostMapping("/orders/{id}/approve")
    public OrderDeskOrderResponse approve(@PathVariable UUID id, @Valid @RequestBody OrderDeskActionRequest request, Authentication auth) {
        return service.approve(auth, id, request.reason());
    }

    @PostMapping("/orders/{id}/reject")
    public OrderDeskOrderResponse reject(@PathVariable UUID id, @Valid @RequestBody OrderDeskActionRequest request, Authentication auth) {
        return service.reject(auth, id, request.reason());
    }

    @GetMapping("/orders/{id}/history")
    public List<OrderStatusHistoryResponse> history(@PathVariable UUID id, Authentication auth) { return service.history(auth, id); }

    @PostMapping(value="/exports/tally", produces="text/csv")
    public ResponseEntity<byte[]> export(@Valid @RequestBody(required=false) OrderDeskExportRequest request, Authentication auth) {
        OrderDeskExportRequest safe = request == null ? new OrderDeskExportRequest(null, null, null) : request;
        OrderDeskService.ExportedCsv csv = service.export(auth, safe);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + csv.filename() + "\"").body(csv.content());
    }

    @GetMapping("/exports")
    public Page<TallyExportBatchResponse> exports(@RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size, Authentication auth) {
        return service.exports(auth, PageRequest.of(Math.max(0,page), Math.min(Math.max(1,size),100), Sort.by("createdAt").descending()));
    }

    @GetMapping("/exports/{id}")
    public TallyExportBatchResponse exportDetails(@PathVariable UUID id, Authentication auth) { return service.exportDetails(auth, id); }
}
