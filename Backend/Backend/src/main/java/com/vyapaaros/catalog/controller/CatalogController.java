package com.vyapaaros.catalog.controller;

import com.vyapaaros.catalog.dto.CategoryResponse;
import com.vyapaaros.catalog.dto.ProductResponse;
import com.vyapaaros.catalog.service.CatalogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> listCategories(
        @RequestParam(required = false) UUID retailerId,
        @RequestParam(required = false) UUID distributorId,
        Authentication authentication
    ) {
        return ResponseEntity.ok(catalogService.listCategories(authentication, retailerId, distributorId));
    }

    @GetMapping("/products")
    public ResponseEntity<Page<ProductResponse>> listProducts(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) UUID retailerId,
        @RequestParam(required = false) UUID distributorId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        Authentication authentication
    ) {
        Pageable pageable = PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
            Sort.by("name").ascending()
        );
        return ResponseEntity.ok(
            catalogService.listProducts(authentication, q, category, pageable, retailerId, distributorId)
        );
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<ProductResponse> getProduct(
        @PathVariable UUID productId,
        @RequestParam(required = false) UUID retailerId,
        @RequestParam(required = false) UUID distributorId,
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            catalogService.getProduct(authentication, productId, retailerId, distributorId)
        );
    }
}
