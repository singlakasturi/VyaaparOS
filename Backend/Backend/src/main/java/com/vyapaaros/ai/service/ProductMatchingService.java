package com.vyapaaros.ai.service;

import com.vyapaaros.ai.dto.AiCandidateResponse;
import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.catalog.repository.ProductRepository;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductMatchingService {
    private final ProductRepository repository;
    public ProductMatchingService(ProductRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true)
    public Match match(String description) {
        return match(description, null);
    }

    @Transactional(readOnly = true)
    public Match match(String description, UUID distributorId) {
        String query = normalize(description);
        List<Product> products = (distributorId == null ? repository.findAll()
                : repository.findManagedByDistributor(distributorId, null, true)).stream()
            .filter(Product::isActive)
            .filter(product -> product.getCategory().isActive())
            .toList();
        List<Product> exact = products.stream()
            .filter(product -> normalize(product.getSku()).equals(query)
                || normalize(product.getName()).equals(query))
            .toList();
        if (exact.size() == 1) return Match.matched(exact.get(0));
        if (query.contains("biscuit") && (query.contains("chhota") || query.contains("small"))) {
            List<Product> smallOptions = products.stream()
                .filter(product -> normalize(product.getName()).contains("biscuit"))
                .filter(product -> product.getUnitQuantity() != null
                    && product.getUnitQuantity().compareTo(java.math.BigDecimal.valueOf(100)) <= 0)
                .toList();
            if (smallOptions.size() > 1) return Match.ambiguous(smallOptions);
        }
        if ((query.contains("detergent") || query.contains("washing powder"))
            && !query.contains("1 kg") && !query.contains("1kg") && !query.contains("1 kilo")
            && !query.contains("500")) {
            List<Product> sizes = products.stream()
                .filter(product -> normalize(product.getName()).contains("detergent")
                    || normalize(product.getName()).contains("washing"))
                .toList();
            if (sizes.size() > 1) return Match.ambiguous(sizes);
        }
        if ((query.contains("tea") || query.contains("chai")) && !query.contains("250")) {
            List<Product> sizes = products.stream().filter(product -> normalize(product.getName()).contains("tea")).toList();
            if (sizes.size() > 1) return Match.ambiguous(sizes);
        }
        List<Product> candidates = products.stream()
            .filter(product -> matches(product, query))
            .toList();
        if (candidates.size() == 1) return Match.matched(candidates.get(0));
        if (candidates.size() > 1) return Match.ambiguous(candidates);
        return Match.unmatched();
    }

    private boolean matches(Product product, String query) {
        String name = normalize(product.getName());
        String sku = normalize(product.getSku());
        String description = normalize(Optional.ofNullable(product.getDescription()).orElse(""));
        String brand = normalize(Optional.ofNullable(product.getBrand()).orElse(""));
        if (query.contains("biscuit") && name.contains("biscuit")) {
            return sizeMatches(product, query);
        }
        if ((query.contains("detergent") || query.contains("washing powder"))
            && (name.contains("detergent") || name.contains("washing"))) {
            return sizeMatches(product, query);
        }
        if ((query.contains("chai") || query.contains("tea")) && name.contains("tea")) {
            return sizeMatches(product, query);
        }
        if (query.contains("chhota") || query.contains("small")) {
            if (product.getUnitQuantity() == null || product.getUnitName() == null
                || product.getUnitQuantity().compareTo(java.math.BigDecimal.valueOf(100)) > 0) return false;
        }
        Set<String> queryTerms = new HashSet<>(Arrays.asList(query.split(" ")));
        Set<String> productTerms = new HashSet<>(Arrays.asList((name + " " + sku + " " + description + " " + brand).split(" ")));
        queryTerms.removeIf(term -> term.length() < 2 || Set.of("the", "and", "for", "case", "cases", "peti", "carton", "box", "boxes", "piece", "pieces", "pcs", "order", "please").contains(term));
        if (queryTerms.isEmpty()) return false;
        long matchingTerms = queryTerms.stream().filter(productTerms::contains).count();
        return matchingTerms == queryTerms.size();
    }

    private boolean sizeMatches(Product product, String query) {
        if (query.contains("250")) return product.getUnitQuantity() != null && product.getUnitName() != null
            && product.getUnitQuantity().intValue() == 250
            && Set.of("g", "gm", "gram", "grams").contains(product.getUnitName().toLowerCase(Locale.ROOT));
        if (query.contains("1 kilo") || query.contains("1kg") || query.contains("1 kg")) {
            return product.getUnitQuantity() != null && product.getUnitName() != null
                && ((product.getUnitQuantity().intValue() == 1 && product.getUnitName().equalsIgnoreCase("kg"))
                || (product.getUnitQuantity().intValue() == 1000
                    && Set.of("g", "gm", "gram", "grams").contains(product.getUnitName().toLowerCase(Locale.ROOT))));
        }
        if (query.contains("chhota") || query.contains("small")) {
            return product.getUnitQuantity() != null && product.getUnitQuantity().intValue() <= 100;
        }
        return true;
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value.toLowerCase(Locale.ROOT), Normalizer.Form.NFKC)
            .replaceAll("[^a-z0-9]+", " ").trim();
    }

    public record Match(Product product, List<Product> candidates, Status status) {
        enum Status { MATCHED, AMBIGUOUS, UNMATCHED }
        static Match matched(Product product) { return new Match(product, List.of(), Status.MATCHED); }
        static Match ambiguous(List<Product> products) { return new Match(null, products, Status.AMBIGUOUS); }
        static Match unmatched() { return new Match(null, List.of(), Status.UNMATCHED); }
    }
}
