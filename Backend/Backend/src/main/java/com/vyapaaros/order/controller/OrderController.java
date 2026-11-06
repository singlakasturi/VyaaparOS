package com.vyapaaros.order.controller;
import com.vyapaaros.order.dto.*;
import com.vyapaaros.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/orders")
public class OrderController{
 private final OrderService service;public OrderController(OrderService s){service=s;}
 @PostMapping public ResponseEntity<OrderResponse> place(@Valid @RequestBody OrderRequest r,Authentication a){return ResponseEntity.status(HttpStatus.CREATED).body(service.place(a,r));}
 @GetMapping("/{id}") public OrderResponse get(@PathVariable UUID id,Authentication a){return service.get(a,id);}
 @GetMapping public Page<OrderResponse> list(@RequestParam UUID retailerId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Authentication a){return service.list(a,retailerId,PageRequest.of(Math.max(0,page),Math.min(Math.max(1,size),100),Sort.by("createdAt").descending()));}
}
