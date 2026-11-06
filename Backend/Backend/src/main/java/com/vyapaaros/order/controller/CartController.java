package com.vyapaaros.order.controller;
import com.vyapaaros.order.dto.*;
import com.vyapaaros.order.service.CartService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/cart")
public class CartController{
 private final CartService service;public CartController(CartService s){service=s;}
 @GetMapping public CartResponse get(@RequestParam UUID retailerId,Authentication a){return service.get(a,retailerId);}
 @PostMapping("/items") public CartResponse add(@Valid @RequestBody CartItemRequest r,Authentication a){return service.upsert(a,r);}
 @PatchMapping("/items/{itemId}") public CartResponse update(@PathVariable UUID itemId,@Valid @RequestBody CartQuantityRequest r,Authentication a){return service.update(a,itemId,r);}
 @DeleteMapping("/items/{itemId}") public ResponseEntity<Void> remove(@PathVariable UUID itemId,Authentication a){service.remove(a,itemId);return ResponseEntity.noContent().build();}
 @DeleteMapping public ResponseEntity<Void> clear(@RequestParam UUID retailerId,Authentication a){service.clear(a,retailerId);return ResponseEntity.noContent().build();}
}
