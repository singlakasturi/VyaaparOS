package com.vyapaaros.pricing.service;

import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.catalog.repository.ProductRepository;
import com.vyapaaros.common.exception.ResourceConflictException;
import com.vyapaaros.common.exception.ResourceNotFoundException;
import com.vyapaaros.distributor.service.DistributorAuthorizationService;
import com.vyapaaros.pricing.dto.PriceRequest;
import com.vyapaaros.pricing.entity.ProductPrice;
import com.vyapaaros.pricing.repository.ProductPriceRepository;
import com.vyapaaros.retailer.entity.Retailer;
import com.vyapaaros.retailer.repository.RetailerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PriceManagementService {

    private final ProductRepository productRepository;
    private final RetailerRepository retailerRepository;
    private final ProductPriceRepository priceRepository;
    private final DistributorAuthorizationService authorization;

    public PriceManagementService(
        ProductRepository productRepository,
        RetailerRepository retailerRepository,
        ProductPriceRepository priceRepository
    ) {
        this(productRepository, retailerRepository, priceRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PriceManagementService(ProductRepository productRepository, RetailerRepository retailerRepository,
        ProductPriceRepository priceRepository, DistributorAuthorizationService authorization) {
        this.productRepository = productRepository;
        this.retailerRepository = retailerRepository;
        this.priceRepository = priceRepository;
        this.authorization = authorization;
    }

    @Transactional
    public ProductPrice upsertDefault(
        Authentication authentication,
        UUID productId,
        PriceRequest request
    ) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        requireManagementAccess(authentication, product.getDistributor().getId());
        ProductPrice price = saveCurrentDefault(product, request);
        return price;
    }

    private ProductPrice saveCurrentDefault(Product product, PriceRequest request) {
        ProductPrice price = priceRepository.findByProductIdAndDistributorIdAndRetailerIsNullAndActiveTrue(
            product.getId(), product.getDistributor().getId()).orElse(null);
        if (price != null && price.getPricePerCase().compareTo(request.pricePerCase()) == 0
            && java.util.Objects.equals(price.getCurrency(), request.currency())
            && java.util.Objects.equals(price.getGstIncluded(), request.gstIncluded())) return price;
        if (price != null) {
            price.setActive(false);
            price.setUpdatedAt(Instant.now());
            priceRepository.save(price);
        }
        price = new ProductPrice();
        initialize(price, product, null);
        apply(price, request);
        return save(price);
    }

    @Transactional
    public ProductPrice upsertOverride(
        Authentication authentication,
        UUID retailerId,
        UUID productId,
        PriceRequest request
    ) {
        Retailer retailer = retailerRepository.findById(retailerId)
            .orElseThrow(() -> new ResourceNotFoundException("Retailer not found."));
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        requireManagementAccess(authentication, product.getDistributor().getId());
        authorization.requireActiveRetailerLink(retailerId, product.getDistributor().getId());
        ProductPrice price = priceRepository
            .findByProductIdAndDistributorIdAndRetailerIdAndActiveTrue(productId, product.getDistributor().getId(), retailerId)
            .orElse(null);
        if (price != null && price.getPricePerCase().compareTo(request.pricePerCase()) == 0
            && java.util.Objects.equals(price.getCurrency(), request.currency())
            && java.util.Objects.equals(price.getGstIncluded(), request.gstIncluded())) return price;
        if (price != null) {
            price.setActive(false);
            price.setUpdatedAt(Instant.now());
            priceRepository.save(price);
        }
        if (price == null || !price.isActive()) price = new ProductPrice();
        initialize(price, product, retailer);
        apply(price, request);
        return save(price);
    }

    @Transactional
    public void deactivateOverride(
        Authentication authentication,
        UUID retailerId,
        UUID productId
    ) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        ProductPrice price = priceRepository
            .findByProductIdAndDistributorIdAndRetailerIdAndActiveTrue(productId, product.getDistributor().getId(), retailerId)
            .orElseThrow(() -> new ResourceNotFoundException("Retailer price not found."));
        requireManagementAccess(authentication, price.getDistributor().getId());
        authorization.requireActiveRetailerLink(retailerId, price.getDistributor().getId());
        price.setActive(false);
        price.setUpdatedAt(Instant.now());
        priceRepository.save(price);
    }

    @Transactional
    public void deactivateDefault(Authentication authentication, UUID productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        requireManagementAccess(authentication, product.getDistributor().getId());
        ProductPrice price = priceRepository.findByProductIdAndDistributorIdAndRetailerIsNullAndActiveTrue(
            productId, product.getDistributor().getId()).orElseThrow(() -> new ResourceNotFoundException("Default price not found."));
        price.setActive(false);
        price.setUpdatedAt(Instant.now());
        priceRepository.save(price);
    }

    @Transactional(readOnly = true)
    public java.util.List<com.vyapaaros.pricing.dto.ManagedPriceResponse> listDefaults(UUID userId) {
        UUID distributorId = businessId(userId);
        return priceRepository.findManagedDefaults(distributorId).stream()
            .map(com.vyapaaros.pricing.dto.ManagedPriceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public com.vyapaaros.pricing.dto.ManagedPriceResponse getDefault(UUID userId, UUID productId) {
        UUID distributorId = businessId(userId);
        Product product = productRepository.findByIdAndDistributorId(productId, distributorId)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        return priceRepository.findByProductIdAndDistributorIdAndRetailerIsNullAndActiveTrue(productId, distributorId)
            .map(com.vyapaaros.pricing.dto.ManagedPriceResponse::from)
            .orElseGet(() -> com.vyapaaros.pricing.dto.ManagedPriceResponse.unpriced(product));
    }

    private void initialize(ProductPrice price, Product product, Retailer retailer) {
        Instant now = Instant.now();
        price.setProduct(product);
        price.setDistributor(product.getDistributor());
        price.setRetailer(retailer);
        price.setActive(true);
        if (price.getCreatedAt() == null) {
            price.setCreatedAt(now);
        }
        price.setUpdatedAt(now);
    }

    private void apply(ProductPrice price, PriceRequest request) {
        price.setPricePerCase(request.pricePerCase());
        price.setCurrency(request.currency());
        price.setGstIncluded(request.gstIncluded());
    }

    private ProductPrice save(ProductPrice price) {
        try {
            return priceRepository.save(price);
        } catch (DataIntegrityViolationException exception) {
            throw new ResourceConflictException("An active price already exists.");
        }
    }

    private void requireManagementAccess(Authentication authentication, UUID distributorId) {
        if (authorization == null || authentication == null || !authentication.isAuthenticated()
            || !(authentication.getPrincipal() instanceof UUID userId)) {
            throw new AccessDeniedException("Price management access denied.");
        }
        authorization.requireOrderDeskAccess(userId, distributorId);
    }

    private UUID businessId(UUID userId) {
        if (authorization == null) throw new AccessDeniedException("Price management access denied.");
        java.util.Set<UUID> ids = authorization.activeOrderDeskBusinessIds(userId);
        if (ids.size() != 1) throw new AccessDeniedException("Select a single authorized distributor workspace.");
        UUID id = ids.iterator().next();
        authorization.requireOrderDeskAccess(userId, id);
        return id;
    }
}
