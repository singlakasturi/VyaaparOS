package com.vyapaaros.ai.service;

import com.vyapaaros.ai.dto.*;
import com.vyapaaros.ai.provider.AiOrderProvider;
import com.vyapaaros.ai.provider.AiProviderException;
import com.vyapaaros.common.exception.ResourceConflictException;
import com.vyapaaros.order.dto.CartItemRequest;
import com.vyapaaros.order.dto.CartResponse;
import com.vyapaaros.order.service.CartService;
import com.vyapaaros.order.service.OrderScopeService;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiOrderCaptureService {
    private final AiOrderProvider provider;
    private final ProductMatchingService matcher;
    private final OrderScopeService scope;
    private final CartService cartService;

    public AiOrderCaptureService(
        AiOrderProvider provider,
        ProductMatchingService matcher,
        OrderScopeService scope,
        CartService cartService
    ) {
        this.provider = provider;
        this.matcher = matcher;
        this.scope = scope;
        this.cartService = cartService;
    }

    public AiOrderParseResponse parse(Authentication authentication, AiOrderParseRequest request) {
        UUID userId = scope.userId(authentication);
        scope.require(userId, request.retailerId(), authentication);
        List<AiProviderItem> extracted = provider.extract(request.text().trim());
        List<AiParsedItemResponse> items = extracted.stream().map(this::resolve).toList();
        long matched = items.stream()
            .filter(item -> item.resolutionStatus() == ProductResolutionStatus.MATCHED
                && item.paidCases() != null)
            .count();
        AiParseStatus status = matched == items.size()
            ? AiParseStatus.PARSED
            : matched == 0 ? AiParseStatus.UNMATCHED : AiParseStatus.PARTIALLY_PARSED;
        List<String> missing = items.stream()
            .filter(item -> item.resolutionStatus() != ProductResolutionStatus.MATCHED)
            .map(AiParsedItemResponse::explanation)
            .toList();
        List<String> quantityWarnings = items.stream()
            .filter(item -> item.resolutionStatus() == ProductResolutionStatus.MATCHED
                && item.paidCases() == null)
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
        scope.require(userId, request.retailerId(), authentication);
        Set<UUID> productIds = new HashSet<>();
        for (AiOrderApplyItem item : request.items()) {
            if (!productIds.add(item.productId())) {
                throw new ResourceConflictException("Duplicate product lines are not allowed.");
            }
        }
        CartResponse response = null;
        for (AiOrderApplyItem item : request.items()) {
            response = cartService.upsert(
                authentication,
                new CartItemRequest(request.retailerId(), item.productId(), item.paidCases())
            );
        }
        return response;
    }

    private AiParsedItemResponse resolve(AiProviderItem item) {
        ProductMatchingService.Match match = matcher.match(item.productDescription());
        return switch (match.status()) {
            case MATCHED -> new AiParsedItemResponse(
                item.originalText(),
                ProductResolutionStatus.MATCHED,
                new AiProductResponse(
                    match.product().getId(),
                    match.product().getSku(),
                    match.product().getName()
                ),
                List.of(),
                item.paidCases() > 0 ? item.paidCases() : null,
                "CASE",
                item.confidence(),
                "Product matched against the active catalogue."
            );
            case AMBIGUOUS -> new AiParsedItemResponse(
                item.originalText(),
                ProductResolutionStatus.AMBIGUOUS,
                null,
                match.candidates().stream()
                    .map(product -> new AiCandidateResponse(
                        product.getId(), product.getSku(), product.getName()
                    ))
                    .toList(),
                item.paidCases() > 0 ? item.paidCases() : null,
                "CASE",
                item.confidence(),
                "Multiple active catalogue products match; choose one explicitly."
            );
            case UNMATCHED -> new AiParsedItemResponse(
                item.originalText(),
                ProductResolutionStatus.UNMATCHED,
                null,
                List.of(),
                item.paidCases() > 0 ? item.paidCases() : null,
                "CASE",
                item.confidence(),
                "No active catalogue product matched this description."
            );
        };
    }
}
