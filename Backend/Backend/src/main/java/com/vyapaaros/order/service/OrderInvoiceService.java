package com.vyapaaros.order.service;

import com.vyapaaros.common.exception.ResourceNotFoundException;
import com.vyapaaros.distributor.service.DistributorAuthorizationService;
import com.vyapaaros.order.entity.Order;
import com.vyapaaros.order.entity.OrderItem;
import com.vyapaaros.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderInvoiceService {
    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.ofPattern("dd MMM uuuu, HH:mm 'UTC'", Locale.ENGLISH).withZone(ZoneId.of("UTC"));
    private final OrderRepository orders;
    private final OrderScopeService scope;
    private final DistributorAuthorizationService distributorAuthorization;

    public OrderInvoiceService(OrderRepository orders, OrderScopeService scope,
        DistributorAuthorizationService distributorAuthorization) {
        this.orders = orders;
        this.scope = scope;
        this.distributorAuthorization = distributorAuthorization;
    }

    @Transactional(readOnly = true)
    public String retailerInvoice(Authentication auth, UUID orderId) {
        Order order = findOrder(orderId);
        UUID userId = scope.userId(auth);
        scope.requireHistoricalOrderAccess(userId, order.getRetailer().getId(), auth);
        return render(order);
    }

    @Transactional(readOnly = true)
    public String orderDeskInvoice(Authentication auth, UUID orderId) {
        if (auth == null || auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
            .noneMatch("ROLE_ORDER_DESK"::equals)) throw new AccessDeniedException("Order Desk access denied.");
        UUID userId = scope.userId(auth);
        Order order = findOrder(orderId);
        distributorAuthorization.requireOrderDeskAccess(userId, order.getDistributor().getId());
        return render(order);
    }

    private Order findOrder(UUID orderId) {
        return orders.findDetailed(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found."));
    }

    private String render(Order order) {
        var retailer = order.getRetailer();
        StringBuilder html = new StringBuilder(4096);
        html.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Order invoice ")
            .append(escape(order.getOrderNumber())).append("</title><style>")
            .append("*{box-sizing:border-box}body{font:14px/1.5 Arial,sans-serif;color:#17202a;margin:0;background:#f2f4f7}.sheet{max-width:900px;margin:32px auto;background:white;padding:44px;box-shadow:0 4px 24px #17202a18}.top{display:flex;justify-content:space-between;gap:24px;border-bottom:2px solid #183b35;padding-bottom:24px}.brand{font-size:26px;font-weight:700;color:#183b35}.muted{color:#65727e}.parties{display:grid;grid-template-columns:1fr 1fr;gap:32px;margin:28px 0}.label{text-transform:uppercase;font-size:11px;letter-spacing:.1em;color:#65727e;font-weight:700}h1{font-size:24px;margin:4px 0}h2{font-size:15px;margin:8px 0}.status{margin:20px 0;padding:12px 16px;background:#f2f7f5;border-radius:8px}.status span{margin-right:20px}table{width:100%;border-collapse:collapse;margin:24px 0}th{text-align:left;background:#f3f5f6;color:#53606b;font-size:12px}th,td{padding:11px 9px;border-bottom:1px solid #e6e9eb;vertical-align:top}.num{text-align:right;white-space:nowrap}.totals{margin-left:auto;width:min(360px,100%)}.totals div{display:flex;justify-content:space-between;padding:5px 0}.grand{font-size:18px;font-weight:700;border-top:2px solid #183b35;margin-top:8px;padding-top:12px!important}.scheme{color:#65727e;font-size:12px}.foot{margin-top:36px;border-top:1px solid #e6e9eb;padding-top:14px;color:#65727e;font-size:12px}@media(max-width:640px){.sheet{margin:0;padding:22px}.top{display:block}.parties{grid-template-columns:1fr;gap:16px}table{font-size:12px}th,td{padding:8px 5px}}@media print{body{background:white}.sheet{margin:0;max-width:none;box-shadow:none;padding:12mm}.no-print{display:none}}")
            .append("</style></head><body><main class=\"sheet\"><header class=\"top\"><div><div class=\"brand\">")
            .append(escape(order.getDistributor().getBusinessName())).append("</div><div class=\"muted\">Order document</div></div><div style=\"text-align:right\"><div class=\"label\">Order invoice</div><h1>")
            .append(escape(order.getOrderNumber())).append("</h1><div class=\"muted\">Order date · ").append(ORDER_DATE.format(order.getCreatedAt())).append("</div></div></header>")
            .append("<section class=\"parties\"><div><div class=\"label\">Distributor</div><h2>").append(escape(order.getDistributor().getBusinessName())).append("</h2></div><div><div class=\"label\">Bill to</div><h2>").append(escape(retailer.getBusinessName())).append("</h2>");
        if (hasText(retailer.getOwnerName())) html.append("<div>").append(escape(retailer.getOwnerName())).append("</div>");
        html.append("<div>").append(escape(retailer.getAddress())).append("</div>");
        if (hasText(retailer.getCity()) || hasText(retailer.getState()) || hasText(retailer.getPincode())) html.append("<div>").append(escape(joinNonEmpty(retailer.getCity(), retailer.getState(), retailer.getPincode()))).append("</div>");
        if (hasText(retailer.getContactPhone())) html.append("<div>Phone · ").append(escape(retailer.getContactPhone())).append("</div>");
        if (hasText(retailer.getContactEmail())) html.append("<div>Email · ").append(escape(retailer.getContactEmail())).append("</div>");
        if (hasText(retailer.getGstin())) html.append("<div>GSTIN · ").append(escape(retailer.getGstin())).append("</div>");
        html.append("</div></section><section class=\"status\"><strong>Current order status:</strong> <span>").append(escape(order.getStatus().name())).append("</span><strong>Current fulfillment status:</strong> <span>").append(escape(order.getFulfillmentStatus().name())).append("</span>");
        if (hasText(order.getTrackingReference())) html.append("<br><strong>Tracking reference:</strong> ").append(escape(order.getTrackingReference()));
        html.append("<div class=\"muted\">Statuses reflect the order now; they are not historical invoice data.</div></section><table><thead><tr><th>Item</th><th>Quantity</th><th class=\"num\">Unit price</th><th class=\"num\">Line total</th></tr></thead><tbody>");
        for (OrderItem item : order.getItems()) {
            html.append("<tr><td><strong>").append(escape(item.getProductName())).append("</strong><br><span class=\"muted\">SKU ").append(escape(item.getProductSku())).append("</span>");
            if (hasText(item.getSchemeName())) html.append("<br><span class=\"scheme\">Recorded offer: ").append(escape(item.getSchemeName())).append(item.getSchemeType() == null ? "" : " (" + escape(item.getSchemeType()) + ")").append("</span>");
            html.append("</td><td>").append(item.getPaidCases()).append(" paid");
            if (item.getFreeCases() > 0) html.append(" + ").append(item.getFreeCases()).append(" free");
            html.append("<br><span class=\"muted\">").append(item.getTotalCases()).append(" total cases</span></td><td class=\"num\">").append(money(item.getUnitPricePerCase(), item.getCurrency())).append("</td><td class=\"num\">").append(money(item.getFinalAmount(), item.getCurrency())).append("</td></tr>");
        }
        html.append("</tbody></table><section class=\"totals\"><div><span>Subtotal</span><strong>").append(money(order.getBaseAmount(), order.getCurrency())).append("</strong></div><div><span>Recorded discounts</span><strong>− ").append(money(order.getDiscountAmount(), order.getCurrency())).append("</strong></div><div class=\"grand\"><span>Order total</span><span>").append(money(order.getFinalAmount(), order.getCurrency())).append("</span></div><div class=\"muted\">Separate tax amount: unavailable in saved order data</div></section><footer class=\"foot\">Generated from the saved order and item snapshots. This document does not change the order.</footer><p class=\"no-print\"><button onclick=\"window.print()\">Print / Save as PDF</button></p></main></body></html>");
        return html.toString();
    }

    private static String money(BigDecimal amount, String currency) {
        return escape(currency) + " " + (amount == null ? "Unavailable" : amount.toPlainString());
    }
    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static String joinNonEmpty(String... values) { return java.util.Arrays.stream(values).filter(OrderInvoiceService::hasText).collect(java.util.stream.Collectors.joining(", ")); }
    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
