package com.vyapaaros.order.dto;
import com.vyapaaros.order.entity.CartItem;
import com.vyapaaros.pricing.dto.ResolvedPrice;
import com.vyapaaros.schemes.dto.SchemeSummary;
import java.math.BigDecimal;
import java.util.UUID;
public record CartItemResponse(UUID id,UUID productId,String sku,String name,int paidCases,int availableCases,int position,
 BigDecimal pricePerCase,String priceSource,SchemeSummary scheme,boolean indicative,
 int indicativeFreeCases,BigDecimal indicativeBaseAmount,BigDecimal indicativeDiscountAmount,
 BigDecimal indicativeFinalAmount,boolean priceAvailable) {
 public static CartItemResponse from(CartItem i,int stock,ResolvedPrice p,SchemeSummary s,
   int freeCases,BigDecimal base,BigDecimal discount,BigDecimal total){
  return new CartItemResponse(i.getId(),i.getProduct().getId(),i.getProduct().getSku(),i.getProduct().getName(),i.getPaidCases(),stock,i.getPosition(),p.pricePerCase(),p.priceSource()==null?null:p.priceSource().name(),s,true,freeCases,base,discount,total,p.priceAvailable());
 }
}
