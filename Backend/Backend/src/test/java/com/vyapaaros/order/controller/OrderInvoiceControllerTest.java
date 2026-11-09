package com.vyapaaros.order.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.vyapaaros.order.service.OrderInvoiceService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;

class OrderInvoiceControllerTest {
    private final OrderInvoiceService service = mock(OrderInvoiceService.class);
    private final OrderInvoiceController controller = new OrderInvoiceController(service);

    @Test void returnsPrivateInlineUtf8HtmlDocument() {
        UUID orderId = UUID.randomUUID();
        Authentication auth = mock(Authentication.class);
        when(service.retailerInvoice(auth, orderId)).thenReturn("<html>invoice</html>");
        var response = controller.retailerInvoice(orderId, auth);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(new MediaType("text", "html", StandardCharsets.UTF_8), response.getHeaders().getContentType());
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).startsWith("inline;"));
        assertEquals("<html>invoice</html>", new String(response.getBody(), StandardCharsets.UTF_8));
    }
}
