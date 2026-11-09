package com.vyapaaros.schemes.controller;
import com.vyapaaros.schemes.dto.*;
import com.vyapaaros.schemes.service.SchemeManagementService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/schemes")
public class SchemeController {
    private final SchemeManagementService service;
    public SchemeController(SchemeManagementService service) { this.service = service; }
    @PostMapping public ResponseEntity<SchemeResponse> create(@Valid @RequestBody SchemeRequest r, Authentication a) {
        return ResponseEntity.status(HttpStatus.CREATED).body(SchemeResponse.from(service.create(a, r)));
    }
    @PutMapping("/{id}") public SchemeResponse update(@PathVariable UUID id, @Valid @RequestBody SchemeRequest r, Authentication a) {
        return SchemeResponse.from(service.update(a, id, r));
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication a) {
        service.deactivate(a, id); return ResponseEntity.noContent().build();
    }
    @PatchMapping("/{id}/active") public SchemeResponse setActive(@PathVariable UUID id, @RequestBody ActiveRequest r, Authentication a) {
        return SchemeResponse.from(service.setActive(a, id, r.active()));
    }
    @GetMapping("/me") public java.util.List<SchemeResponse> managed(@RequestParam(required=false) String q,
        @RequestParam(required=false) Boolean active, Authentication a) {
        return service.listManaged(a, q, active).stream().map(SchemeResponse::from).toList();
    }
    @GetMapping("/retailer/me") public java.util.List<SchemeResponse> retailer(@RequestParam UUID retailerId, Authentication a) {
        if (a == null || a.getAuthorities().stream().map(GrantedAuthority::getAuthority).noneMatch("ROLE_RETAILER"::equals))
            throw new AccessDeniedException("Retailer access required.");
        return service.retailerApplicable(a, retailerId).stream().map(SchemeResponse::from).toList();
    }
    @GetMapping("/retailer/me/products/{productId}") public java.util.List<SchemeResponse> retailerProduct(
        @PathVariable UUID productId, @RequestParam UUID retailerId, Authentication a) {
        if (a == null || a.getAuthorities().stream().map(GrantedAuthority::getAuthority).noneMatch("ROLE_RETAILER"::equals))
            throw new AccessDeniedException("Retailer access required.");
        return service.applicableRetailerProduct(a, retailerId, productId).stream().map(SchemeResponse::from).toList();
    }
    @GetMapping public java.util.List<SchemeResponse> list(
        @RequestParam(required = false) UUID distributorId,
        @RequestParam(required = false) UUID productId,
        @RequestParam(required = false) UUID retailerId,
        @RequestParam(required = false) Boolean active,
        Authentication a
    ) {
        if (distributorId == null) return service.listManaged(a, null, active).stream().map(SchemeResponse::from).toList();
        return service.list(a, distributorId, productId, retailerId, active).stream().map(SchemeResponse::from).toList();
    }
    public record ActiveRequest(boolean active) {}
}
