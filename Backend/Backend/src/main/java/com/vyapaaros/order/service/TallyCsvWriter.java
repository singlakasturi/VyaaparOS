package com.vyapaaros.order.service;

import com.vyapaaros.order.entity.*;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.*;

final class TallyCsvWriter {
    private static final String HEADER = "Voucher Type,Voucher Date,Order Number,Retailer / Party Name,Retailer Contact,Product SKU,Product Name,Paid Cases,Free Cases,Total Cases,Rate Per Case,Base Amount,Discount Amount,Final Line Amount,Order Total,Currency,GST Included\n";
    private TallyCsvWriter() {}
    static byte[] write(List<Order> orders) {
        StringBuilder csv = new StringBuilder(HEADER);
        for (Order order : orders) {
            for (OrderItem item : order.getItems()) {
                csv.append(row("Sales", order.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate().toString(),
                    order.getOrderNumber(), order.getRetailer().getBusinessName(), order.getRetailer().getContactPhone(),
                    item.getProductSku(), item.getProductName(), item.getPaidCases(), item.getFreeCases(),
                    item.getTotalCases(), item.getUnitPricePerCase(), item.getBaseAmount(), item.getDiscountAmount(),
                    item.getFinalAmount(), order.getFinalAmount(), item.getCurrency(),
                    item.getGstIncluded() == null ? "" : item.getGstIncluded().toString()));
            }
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static String row(Object... values) {
        return Arrays.stream(values).map(v -> escape(String.valueOf(v))).reduce((a,b) -> a + "," + b).orElse("") + "\n";
    }
    private static String escape(String value) {
        String safe = value;
        int firstVisible = 0;
        while (firstVisible < safe.length()
            && (safe.charAt(firstVisible) <= 0x20 || safe.charAt(firstVisible) == '\uFEFF')) {
            firstVisible++;
        }
        if (firstVisible < safe.length() && "=+-@".indexOf(safe.charAt(firstVisible)) >= 0) {
            safe = "'" + safe;
        }
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            safe = "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
