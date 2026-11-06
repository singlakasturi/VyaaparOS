package com.vyapaaros.order.dto;
import com.vyapaaros.order.entity.Cart;
import java.util.*;
public record CartResponse(UUID id,UUID retailerId,List<CartItemResponse> items) {
 public static CartResponse from(Cart c,List<CartItemResponse> items){return new CartResponse(c.getId(),c.getRetailer().getId(),items);}
}
