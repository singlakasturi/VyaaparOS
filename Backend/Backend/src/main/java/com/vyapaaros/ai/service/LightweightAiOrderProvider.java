package com.vyapaaros.ai.service;

import com.vyapaaros.ai.dto.AiProviderItem;
import com.vyapaaros.ai.provider.AiOrderProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic fallback for common WhatsApp order lines. It only recognizes
 * explicit positive integer quantities followed by product text.
 */
@Component
public class LightweightAiOrderProvider implements AiOrderProvider {
    private static final Pattern QUANTITY_FIRST = Pattern.compile(
        "(?i)(\\d+)\\s+(?:(peti|cases?|cartons?|boxes?|pcs?|pieces?)\\s+)?(.+?)(?=\\s+\\d+\\s+(?!(?:kilo(?:gram)?s?|kg)\\b)(?:(?:peti|cases?|cartons?|boxes?|pcs?|pieces?)\\s+)?|\\s+aur\\s+|\\s+and\\s+|[,;\\n]|$)"
    );
    private static final Pattern PRODUCT_FIRST = Pattern.compile(
        "(?i)(?:^|[,;\\n])\\s*(.+?)\\s+(\\d+)\\s+(peti|cases?|cartons?|boxes?|pcs?|pieces?)(?=$|[,;\\n]|\\s+aur\\s+|\\s+and\\s+)"
    );

    @Override
    public List<AiProviderItem> extract(String text) {
        List<AiProviderItem> items = new ArrayList<>();
        Matcher first = QUANTITY_FIRST.matcher(text);
        while (first.find()) add(items, first.group(0), first.group(3), first.group(1), first.group(2));
        Matcher productFirst = PRODUCT_FIRST.matcher(text);
        while (productFirst.find()) add(items, productFirst.group(0), productFirst.group(1),
            productFirst.group(2), productFirst.group(3));
        Matcher mixed = Pattern.compile("(?i)(.+?)\\s+(\\d+)\\s+(peti|cases?|cartons?|boxes?|pcs?|pieces?)\\s+(?:aur|and)\\s+(\\d+)\\s+(.+)").matcher(text);
        while (mixed.find()) {
            add(items, mixed.group(0), mixed.group(1), mixed.group(2), mixed.group(3));
            add(items, mixed.group(0), mixed.group(5), mixed.group(4), null);
        }
        return items.stream().collect(java.util.stream.Collectors.toMap(
            item -> item.productDescription().toLowerCase(java.util.Locale.ROOT) + "|" + item.paidCases() + "|" + item.unit(),
            item -> item, (firstItem, ignored) -> firstItem, java.util.LinkedHashMap::new
        )).values().stream().toList();
    }

    private void add(List<AiProviderItem> items, String original, String description, String quantity, String unitLabel) {
        String product = description.trim()
            .replaceAll("(?i)\\b(?:of|please|bhej do|bhej dena|wala|wali|ka|ki)\\b", "")
            .replaceAll("(?i)\\b(?:250|500|750|1000)\\s*(?:g|gm|gram|grams)\\b", "")
            .trim();
        if (product.isBlank()) return;
        int cases;
        try { cases = Integer.parseInt(quantity); } catch (NumberFormatException ignored) { return; }
        if (cases < 1 || cases > 10000) return;
        String unit = unitLabel != null && unitLabel.toLowerCase().matches("pcs?|pieces?")
            ? "PIECE" : "CASE";
        boolean ambiguous = unitLabel == null;
        items.add(new AiProviderItem(original.trim(), product, cases, unit, ambiguous, 0.55));
    }
}
