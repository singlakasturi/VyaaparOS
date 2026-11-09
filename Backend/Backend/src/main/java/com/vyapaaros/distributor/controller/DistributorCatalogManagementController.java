package com.vyapaaros.distributor.controller;

import com.vyapaaros.catalog.dto.*;
import com.vyapaaros.distributor.service.DistributorCatalogManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/distributor/me/catalog")
public class DistributorCatalogManagementController {
    private final DistributorCatalogManagementService catalog;
    public DistributorCatalogManagementController(DistributorCatalogManagementService catalog){this.catalog=catalog;}

    @GetMapping("/categories")
    public List<CategoryResponse> categories(Authentication authentication){return catalog.categories(userId(authentication));}
    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryCreateRequest request,Authentication authentication){return ResponseEntity.status(HttpStatus.CREATED).body(catalog.createCategory(userId(authentication),request.name(),request.slug(),request.description()));}
    @GetMapping("/products")
    public List<ManagedProductResponse> products(@RequestParam(required=false) String q,@RequestParam(required=false) Boolean active,Authentication authentication){return catalog.listProducts(userId(authentication),q,active);}
    @GetMapping("/products/{productId}")
    public ManagedProductResponse product(@PathVariable UUID productId,Authentication authentication){return catalog.getProduct(userId(authentication),productId);}
    @PostMapping("/products")
    public ResponseEntity<ManagedProductResponse> create(@Valid @RequestBody ProductUpsertRequest request,Authentication authentication){return ResponseEntity.status(HttpStatus.CREATED).body(catalog.createProduct(userId(authentication),request));}
    @PutMapping("/products/{productId}")
    public ManagedProductResponse update(@PathVariable UUID productId,@Valid @RequestBody ProductUpsertRequest request,Authentication authentication){return catalog.updateProduct(userId(authentication),productId,request);}
    @PatchMapping("/products/{productId}/active")
    public ManagedProductResponse setActive(@PathVariable UUID productId,@Valid @RequestBody SetActiveRequest request,Authentication authentication){return catalog.setProductActive(userId(authentication),productId,request.active());}

    private UUID userId(Authentication a){if(a==null||!a.isAuthenticated()||!(a.getPrincipal() instanceof UUID id))throw new AuthenticationCredentialsNotFoundException("Authentication required.");return id;}
    public record SetActiveRequest(@NotNull Boolean active){}
}
