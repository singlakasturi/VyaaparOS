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
        String query = normalize(description);
        List<Product> products = repository.findAll().stream()
            .filter(Product::isActive)
            .filter(product -> product.getCategory().isActive())
            .toList();
        List<Product> exact = products.stream()
            .filter(product -> normalize(product.getSku()).equals(query)
                || normalize(product.getName()).equals(query))
            .toList();
        if (exact.size() == 1) return Match.matched(exact.get(0));
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
        return name.contains(query) || sku.contains(query) || description.contains(query) || brand.contains(query);
    }

    private boolean sizeMatches(Product product, String query) {
        if (query.contains("250")) return product.getUnitQuantity().intValue() == 250
            && product.getUnitName().equalsIgnoreCase("g");
        if (query.contains("1 kilo") || query.contains("1kg") || query.contains("1 kg")) {
            return product.getUnitQuantity().intValue() == 1
                && product.getUnitName().equalsIgnoreCase("kg");
        }
        if (query.contains("chhota") || query.contains("small")) {
            return product.getUnitQuantity().intValue() <= 100;
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
