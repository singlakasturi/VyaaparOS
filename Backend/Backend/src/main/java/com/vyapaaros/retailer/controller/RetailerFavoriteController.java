package com.vyapaaros.retailer.controller;

import com.vyapaaros.retailer.dto.RetailerFavoriteResponse;
import com.vyapaaros.retailer.service.RetailerFavoriteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/retailer/me/favorites")
public class RetailerFavoriteController {
    private final RetailerFavoriteService favorites;

    public RetailerFavoriteController(RetailerFavoriteService favorites) { this.favorites = favorites; }

    @GetMapping
    public List<RetailerFavoriteResponse> list(@RequestParam UUID retailerId, Authentication authentication) {
        return favorites.list(authentication, retailerId);
    }

    @PostMapping
    public ResponseEntity<RetailerFavoriteResponse> add(@RequestParam UUID retailerId,
        @Valid @RequestBody FavoriteRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(favorites.add(authentication, retailerId, request.productId()));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> remove(@PathVariable UUID productId, @RequestParam UUID retailerId,
        Authentication authentication) {
        favorites.remove(authentication, retailerId, productId);
        return ResponseEntity.noContent().build();
    }

    public record FavoriteRequest(@NotNull UUID productId) { }
}
