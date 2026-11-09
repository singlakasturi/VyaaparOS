package com.vyapaaros.inventory.controller;

import com.vyapaaros.inventory.dto.InventoryAdjustmentRequest;
import com.vyapaaros.inventory.dto.InventoryResponse;
import com.vyapaaros.inventory.dto.LowStockThresholdRequest;
import com.vyapaaros.inventory.dto.InventoryMovementResponse;
import com.vyapaaros.inventory.dto.InventoryReconciliationResponse;
import com.vyapaaros.inventory.entity.InventoryMovementType;
import com.vyapaaros.inventory.service.DistributorInventoryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/distributor/me/inventory")
public class DistributorInventoryController {
    private final DistributorInventoryService inventory;
    public DistributorInventoryController(DistributorInventoryService inventory){this.inventory=inventory;}
    @GetMapping public org.springframework.data.domain.Page<InventoryResponse> list(@RequestParam(required=false) String q,
      @RequestParam(defaultValue="false") boolean lowStock,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Authentication a){
        Pageable pageable=PageRequest.of(Math.max(0,page),Math.min(Math.max(1,size),100),Sort.by("product.name").ascending().and(Sort.by("product.sku").ascending()));
        return inventory.list(userId(a),q,lowStock,pageable);
    }
    @GetMapping("/products/{productId}") public InventoryResponse get(@PathVariable UUID productId,Authentication a){return inventory.get(userId(a),productId);}
    @PostMapping("/products/{productId}/adjustments") public ResponseEntity<InventoryResponse> adjust(@PathVariable UUID productId,@Valid @RequestBody InventoryAdjustmentRequest request,Authentication a){return ResponseEntity.status(HttpStatus.CREATED).body(inventory.adjust(userId(a),productId,request.quantityDelta(),request.reason()));}
    @PutMapping("/products/{productId}/threshold") public InventoryResponse updateThreshold(@PathVariable UUID productId,@Valid @RequestBody LowStockThresholdRequest request,Authentication a){return inventory.updateThreshold(userId(a),productId,request.lowStockThreshold());}
    @GetMapping("/movements") public org.springframework.data.domain.Page<InventoryMovementResponse> movementHistory(
      @RequestParam(required=false) UUID productId,@RequestParam(required=false) InventoryMovementType type,
      @RequestParam(required=false) Instant from,@RequestParam(required=false) Instant until,
      @RequestParam(required=false) String orderReference,@RequestParam(defaultValue="0") int page,
      @RequestParam(defaultValue="20") int size,Authentication a){
        return inventory.movementHistory(userId(a),productId,type,from,until,orderReference,page,size);
    }
    @GetMapping("/products/{productId}/reconciliation") public InventoryReconciliationResponse reconcile(@PathVariable UUID productId,Authentication a){return inventory.reconcile(userId(a),productId);}
    private UUID userId(Authentication a){if(a==null||!a.isAuthenticated()||!(a.getPrincipal() instanceof UUID id))throw new AuthenticationCredentialsNotFoundException("Authentication required.");return id;}
}
