package com.vyapaaros.schemes.controller;
import com.vyapaaros.schemes.dto.*;
import com.vyapaaros.schemes.service.SchemeManagementService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
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
    @GetMapping public java.util.List<SchemeResponse> list(
        @RequestParam UUID distributorId,
        @RequestParam(required = false) UUID productId,
        @RequestParam(required = false) UUID retailerId,
        @RequestParam(required = false) Boolean active,
        Authentication a
    ) {
        return service.list(a, distributorId, productId, retailerId, active).stream().map(SchemeResponse::from).toList();
    }
}
