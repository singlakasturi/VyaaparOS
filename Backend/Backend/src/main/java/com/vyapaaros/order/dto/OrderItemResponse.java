package com.vyapaaros.order.dto;
import com.vyapaaros.order.entity.OrderItem;
import java.math.BigDecimal;
import java.util.UUID;
public record OrderItemResponse(UUID productId,String sku,String name,int paidCases,int freeCases,int totalCases,
 BigDecimal unitPricePerCase,String currency,Boolean gstIncluded,BigDecimal baseAmount,BigDecimal discountAmount,
 BigDecimal finalAmount,UUID appliedSchemeId,String schemeName,String schemeType) {
 public static OrderItemResponse from(OrderItem i){return new OrderItemResponse(i.getProduct().getId(),i.getProductSku(),i.getProductName(),i.getPaidCases(),i.getFreeCases(),i.getTotalCases(),i.getUnitPricePerCase(),i.getCurrency(),i.getGstIncluded(),i.getBaseAmount(),i.getDiscountAmount(),i.getFinalAmount(),i.getAppliedSchemeId(),i.getSchemeName(),i.getSchemeType());}
}
