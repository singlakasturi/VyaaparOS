package com.vyapaaros.ai.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.catalog.entity.ProductCategory;
import com.vyapaaros.catalog.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductMatchingServiceTest {
    private final ProductRepository repository = mock(ProductRepository.class);
    private final ProductMatchingService service = new ProductMatchingService(repository);

    @Test
    void matchesHindiHinglishBiscuitDescriptionToActiveCatalogueProduct() {
        Product product = product("SKU-BS-075", "Glucose biscuit 75 g", "g", "75");
        when(repository.findAll()).thenReturn(List.of(product));

        var result = service.match("biscuit chhota");

        assertEquals(ProductMatchingService.Match.Status.MATCHED, result.status());
        assertEquals(product, result.product());
    }

    @Test
    void matchesTeaByHindiNameAndSize() {
        Product product = product("SKU-TE-250", "Tea leaf 250 g", "g", "250");
        when(repository.findAll()).thenReturn(List.of(product));

        assertEquals(ProductMatchingService.Match.Status.MATCHED, service.match("chai patti 250 wala").status());
    }

    @Test
    void doesNotMatchInactiveProducts() {
        Product product = product("SKU-BS-075", "Glucose biscuit 75 g", "g", "75");
        product.setActive(false);
        when(repository.findAll()).thenReturn(List.of(product));

        assertEquals(ProductMatchingService.Match.Status.UNMATCHED, service.match("biscuit").status());
    }

    private Product product(String sku, String name, String unitName, String quantity) {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setSku(sku);
        product.setName(name);
        product.setUnitName(unitName);
        product.setUnitQuantity(new BigDecimal(quantity));
        product.setActive(true);
        ProductCategory category = new ProductCategory();
        category.setActive(true);
        product.setCategory(category);
        return product;
    }
}
