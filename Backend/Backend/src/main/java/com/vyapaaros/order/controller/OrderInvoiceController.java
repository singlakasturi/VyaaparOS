package com.vyapaaros.order.controller;

import com.vyapaaros.order.service.OrderInvoiceService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderInvoiceController {
    private final OrderInvoiceService invoices;
    public OrderInvoiceController(OrderInvoiceService invoices) { this.invoices = invoices; }

    @GetMapping(value = "/api/orders/{id}/invoice", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<byte[]> retailerInvoice(@PathVariable UUID id, Authentication auth) {
        return response(invoices.retailerInvoice(auth, id), id);
    }

    @GetMapping(value = "/api/order-desk/orders/{id}/invoice", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<byte[]> orderDeskInvoice(@PathVariable UUID id, Authentication auth) {
        return response(invoices.orderDeskInvoice(auth, id), id);
    }

    private ResponseEntity<byte[]> response(String html, UUID id) {
        return ResponseEntity.ok().contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"order-" + id + "-invoice.html\"")
            .body(html.getBytes(StandardCharsets.UTF_8));
    }
}
