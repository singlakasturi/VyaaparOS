package com.vyapaaros.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.List;

class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
        .standaloneSetup(new AccessDeniedController())
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    @Test
    void mapsAccessDeniedExceptionToForbiddenResponse() throws Exception {
        mockMvc.perform(get("/test/access-denied"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.message").value("Access denied."))
            .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void mapsStockConflictToStructuredProductAndAvailabilityData() throws Exception {
        MockMvc stockMvc = MockMvcBuilders.standaloneSetup(new StockConflictController())
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        stockMvc.perform(get("/test/stock-conflict"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.data.code").value("INSUFFICIENT_STOCK"))
            .andExpect(jsonPath("$.data.items[0].sku").value("SKU-TE-250"))
            .andExpect(jsonPath("$.data.items[0].productName").value("Tea leaf 250 g"))
            .andExpect(jsonPath("$.data.items[0].requestedCases").value(15))
            .andExpect(jsonPath("$.data.items[0].availableCases").value(0));
    }

    @RestController
    private static class AccessDeniedController {

        @GetMapping("/test/access-denied")
        void accessDenied() {
            throw new AccessDeniedException("internal detail");
        }
    }

    @RestController
    private static class StockConflictController {
        @GetMapping("/test/stock-conflict")
        void stockConflict() {
            throw new InsufficientStockException(List.of(
                new InsufficientStockException.StockIssue("SKU-TE-250", "Tea leaf 250 g", 15, 0)
            ));
        }
    }
}
