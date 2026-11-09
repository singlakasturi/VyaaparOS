package com.vyapaaros.ai.service;

import com.vyapaaros.ai.dto.*;
import com.vyapaaros.ai.provider.AiOrderProvider;
import com.vyapaaros.ai.provider.AiProviderException;
import com.vyapaaros.catalog.dto.ProductResponse;
import com.vyapaaros.catalog.service.CatalogService;
import com.vyapaaros.order.dto.CartItemRequest;
import com.vyapaaros.order.dto.CartResponse;
import com.vyapaaros.order.service.CartService;
import com.vyapaaros.order.service.OrderScopeService;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

@Service
public class AiOrderCaptureService {
    private final AiOrderProvider provider;
    private final ProductMatchingService matcher;
    private final OrderScopeService scope;
    private final CartService cartService;
    private final CatalogService catalogService;

    public AiOrderCaptureService(
        AiOrderProvider provider,
        ProductMatchingService matcher,
        OrderScopeService scope,
        CartService cartService
    ) {
        this(provider, matcher, scope, cartService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AiOrderCaptureService(AiOrderProvider provider, ProductMatchingService matcher,
        OrderScopeService scope, CartService cartService, CatalogService catalogService) {
        this.provider = provider;
        this.matcher = matcher;
        this.scope = scope;
        this.cartService = cartService;
        this.catalogService = catalogService;
    }

    public AiOrderParseResponse parse(Authentication authentication, AiOrderParseRequest request) {
        UUID userId = scope.userId(authentication);
        scope.require(userId, request.retailerId(), authentication);
        List<AiProviderItem> extracted;
        try {
            extracted = provider.extract(request.text().trim());
        } catch (AiProviderException exception) {
            // A configured provider may be unavailable; the local parser stays
            // available and never supplies product identities.
            extracted = new LightweightAiOrderProvider().extract(request.text().trim());
        }
        UUID distributorId = scope.currentDistributorForRetailer(request.retailerId()).getId();
        List<AiParsedItemResponse> items = extracted.stream()
            .map(item -> resolve(item, request.retailerId(), distributorId, authentication))
            .toList();
        long matched = items.stream()
            .filter(item -> item.resolutionStatus() == ProductResolutionStatus.MATCHED
                && item.paidCases() != null && !item.unitAmbiguous())
            .count();
        AiParseStatus status = matched == items.size()
            ? AiParseStatus.PARSED
            : matched == 0 ? AiParseStatus.UNMATCHED : AiParseStatus.PARTIALLY_PARSED;
        List<String> missing = items.stream()
            .filter(item -> item.resolutionStatus() != ProductResolutionStatus.MATCHED || item.unitAmbiguous())
            .map(AiParsedItemResponse::explanation)
            .toList();
        List<String> quantityWarnings = items.stream()
            .filter(item -> item.resolutionStatus() == ProductResolutionStatus.MATCHED
                && (item.paidCases() == null || item.unitAmbiguous()))
            .map(item -> "Quantity is missing for " + item.originalText() + ".")
            .toList();
        return new AiOrderParseResponse(
            UUID.randomUUID(),
            request.text(),
            status,
            items,
            java.util.stream.Stream.concat(missing.stream(), quantityWarnings.stream()).toList()
        );
    }

    @Transactional
    public CartResponse apply(Authentication authentication, AiOrderApplyRequest request) {
        UUID userId = scope.userId(authentication);
        if (!scope.has(authentication, "ROLE_RETAILER") && !scope.has(authentication, "ROLE_ORDER_DESK")) {
            throw new AccessDeniedException("Order capture access denied.");
        }
        if (scope.has(authentication, "ROLE_ORDER_DESK")) {
            throw new AccessDeniedException("Order desk users can prepare a retailer cart, but only the retailer can add items to it.");
        }
        scope.require(userId, request.retailerId(), authentication);
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        for (AiOrderApplyItem item : request.items()) {
            if (!"CASE".equalsIgnoreCase(item.unit())) {
                throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Only case quantities can be added to this cart. Confirm or re-enter the quantity as cases.");
            }
            quantities.merge(item.productId(), item.paidCases(), Math::addExact);
        }
        CartResponse response = null;
        for (var item : quantities.entrySet()) {
            response = cartService.upsert(
                authentication,
                new CartItemRequest(request.retailerId(), item.getKey(), item.getValue())
            );
        }
        if (response == null) {
            response = cartService.get(authentication, request.retailerId());
        }
        return response;
    }

    private AiParsedItemResponse resolve(AiProviderItem item, UUID retailerId, UUID distributorId,
        Authentication authentication) {
        ProductMatchingService.Match match = matcher.match(item.productDescription(), distributorId);
        return switch (match.status()) {
            case MATCHED -> {
                ProductResponse product = catalogService == null ? null : catalogService.getProduct(authentication,
                    match.product().getId(), retailerId, null);
                var details = product == null ? new AiProductResponse(match.product().getId(),
                    match.product().getSku(), match.product().getName(), false, null, null, 0, false, null)
                    : new AiProductResponse(product.id(), product.sku(), product.name(), product.priceAvailable(),
                        product.pricePerCase(), product.currency(), product.availableCases(),
                        product.schemeAvailable(), product.schemeName());
                yield new AiParsedItemResponse(item.originalText(), ProductResolutionStatus.MATCHED,
                    details,
                    List.of(), item.paidCases() > 0 ? item.paidCases() : null, item.unit(),
                    item.unitAmbiguous() || (!"CASE".equalsIgnoreCase(item.unit()) && !"PIECE".equalsIgnoreCase(item.unit())),
                    item.confidence(), "Matched in the current retailer catalogue.");
            }
            case AMBIGUOUS -> new AiParsedItemResponse(
                item.originalText(),
                ProductResolutionStatus.AMBIGUOUS,
                null,
                match.candidates().stream()
                    .map(candidate -> {
                        ProductResponse product = catalogService == null ? null
                            : catalogService.getProduct(authentication, candidate.getId(), retailerId, null);
                        return product == null ? new AiCandidateResponse(candidate.getId(), candidate.getSku(),
                            candidate.getName(), false, null, null, 0, false, null)
                            : new AiCandidateResponse(product.id(), product.sku(), product.name(),
                                product.priceAvailable(), product.pricePerCase(), product.currency(),
                                product.availableCases(), product.schemeAvailable(), product.schemeName());
                    })
                    .toList(),
                item.paidCases() > 0 ? item.paidCases() : null,
                item.unit(),
                item.unitAmbiguous() || (!"CASE".equalsIgnoreCase(item.unit()) && !"PIECE".equalsIgnoreCase(item.unit())),
                item.confidence(),
                "Multiple active catalogue products match; choose one explicitly."
            );
            case UNMATCHED -> new AiParsedItemResponse(
                item.originalText(),
                ProductResolutionStatus.UNMATCHED,
                null,
                List.of(),
                item.paidCases() > 0 ? item.paidCases() : null,
                item.unit(),
                item.unitAmbiguous() || (!"CASE".equalsIgnoreCase(item.unit()) && !"PIECE".equalsIgnoreCase(item.unit())),
                item.confidence(),
                "No active catalogue product matched this description."
            );
        };
    }
}
