package com.vyapaaros.inventory.service;

import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.catalog.repository.ProductRepository;
import com.vyapaaros.common.exception.ResourceNotFoundException;
import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.distributor.repository.DistributorBusinessRepository;
import com.vyapaaros.distributor.service.DistributorAuthorizationService;
import com.vyapaaros.inventory.dto.*;
import com.vyapaaros.inventory.entity.*;
import com.vyapaaros.inventory.repository.*;
import com.vyapaaros.inventory.entity.InventoryMovementType;
import com.vyapaaros.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.time.Instant;
import java.util.*;

@Service
public class DistributorInventoryService {
    private final DistributorAuthorizationService authorization;
    private final DistributorBusinessRepository businesses;
    private final ProductRepository products;
    private final InventoryRepository inventory;
    private final InventoryAdjustmentRepository adjustments;
    private final UserRepository users;
    private final InventoryMovementRepository movements;
    private final InventoryMovementService movementService;

    public DistributorInventoryService(DistributorAuthorizationService authorization,DistributorBusinessRepository businesses,
      ProductRepository products,InventoryRepository inventory,InventoryAdjustmentRepository adjustments,UserRepository users){
        this(authorization,businesses,products,inventory,adjustments,users,null,null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DistributorInventoryService(DistributorAuthorizationService authorization,DistributorBusinessRepository businesses,
      ProductRepository products,InventoryRepository inventory,InventoryAdjustmentRepository adjustments,UserRepository users,
      InventoryMovementRepository movements,InventoryMovementService movementService){
        this.authorization=authorization;this.businesses=businesses;this.products=products;this.inventory=inventory;this.adjustments=adjustments;this.users=users;
        this.movements=movements;this.movementService=movementService;
    }

    @Transactional(readOnly=true)
    public Page<InventoryResponse> list(UUID userId,String query,boolean lowStock,Pageable pageable){
        UUID businessId=businessId(userId);String q=query==null||query.isBlank()?null:query.trim().toLowerCase(Locale.ROOT);
        return inventory.searchForDistributor(businessId,q,lowStock,pageable).map(i->InventoryResponse.from(i,List.of()));
    }

    @Transactional(readOnly=true)
    public InventoryResponse get(UUID userId,UUID productId){
        UUID businessId=businessId(userId);Product p=products.findByIdAndDistributorId(productId,businessId).orElseThrow(()->new ResourceNotFoundException("Product not found."));
        Inventory row=inventory.findByProductIdAndDistributorId(p.getId(),businessId).orElseThrow(()->new ResourceNotFoundException("Inventory not found."));
        return InventoryResponse.from(row,adjustments.findTop100ByInventoryIdAndDistributorIdOrderByCreatedAtDescIdDesc(row.getId(),businessId).stream().map(InventoryAdjustmentResponse::from).toList());
    }

    @Transactional
    public InventoryResponse adjust(UUID userId,UUID productId,Integer quantityDelta,String reason){
        UUID businessId=businessId(userId);if(quantityDelta==null||quantityDelta==0)throw new IllegalArgumentException("Adjustment quantity must be a non-zero number of cases.");
        if(reason==null||reason.isBlank())throw new IllegalArgumentException("An adjustment reason is required.");String cleanReason=reason.trim();if(cleanReason.length()>500)throw new IllegalArgumentException("Adjustment reason must be 500 characters or fewer.");
        Product product=products.findByIdAndDistributorId(productId,businessId).orElseThrow(()->new ResourceNotFoundException("Product not found."));
        Inventory row=inventory.lockByProductIdAndDistributorId(productId,businessId).orElseThrow(()->new ResourceNotFoundException("Inventory not found."));
        long available=(long)row.getAvailableCases()+quantityDelta;long onHand=(long)row.getOnHandCases()+quantityDelta;
        if(available<0)throw new IllegalArgumentException("Adjustment cannot reduce on-hand stock below reserved stock.");
        if(available>Integer.MAX_VALUE||onHand>Integer.MAX_VALUE)throw new IllegalArgumentException("Adjusted stock exceeds the supported limit.");
        int current=(int)available;row.setAvailableCases(current);row.setOnHandCases((int)onHand);row.setUpdatedAt(Instant.now());
        InventoryAdjustment adjustment=new InventoryAdjustment();adjustment.setInventory(row);adjustment.setProduct(product);adjustment.setDistributor(businesses.getReferenceById(businessId));adjustment.setActor(users.getReferenceById(userId));adjustment.setQuantityDelta(quantityDelta);adjustment.setQuantityAfter(current);adjustment.setReason(cleanReason);adjustment.setCreatedAt(Instant.now());
        inventory.save(row);adjustments.save(adjustment);
        if(movementService!=null) movementService.manualAdjustment(adjustment);
        return InventoryResponse.from(row,adjustments.findTop100ByInventoryIdAndDistributorIdOrderByCreatedAtDescIdDesc(row.getId(),businessId).stream().map(InventoryAdjustmentResponse::from).toList());
    }

    @Transactional
    public InventoryResponse updateThreshold(UUID userId,UUID productId,Integer threshold){
        UUID businessId=businessId(userId);
        if(threshold==null||threshold<0)throw new IllegalArgumentException("Low-stock threshold must be zero or greater.");
        Product product=products.findByIdAndDistributorId(productId,businessId).orElseThrow(()->new ResourceNotFoundException("Product not found."));
        Inventory row=inventory.lockByProductIdAndDistributorId(productId,businessId).orElseThrow(()->new ResourceNotFoundException("Inventory not found."));
        row.setLowStockThreshold(threshold);row.setUpdatedAt(Instant.now());inventory.save(row);
        return InventoryResponse.from(row,adjustments.findTop100ByInventoryIdAndDistributorIdOrderByCreatedAtDescIdDesc(row.getId(),businessId).stream().map(InventoryAdjustmentResponse::from).toList());
    }

    @Transactional(readOnly=true)
    public Page<InventoryMovementResponse> movementHistory(UUID userId,UUID productId,InventoryMovementType type,
        Instant from,Instant until,String orderReference,int page,int size){
        UUID businessId=businessId(userId);
        if(page<0||size<1||size>100)throw new IllegalArgumentException("Page must be zero or greater and size must be between 1 and 100.");
        if(from!=null&&until!=null&&!until.isAfter(from))throw new IllegalArgumentException("End date must be later than start date.");
        String orderQuery=orderReference==null||orderReference.isBlank()?null:orderReference.trim().toLowerCase(Locale.ROOT);
        if(orderQuery!=null&&orderQuery.length()>100)throw new IllegalArgumentException("Order reference must be 100 characters or fewer.");
        UUID orderId = null;
        if (orderQuery != null) {
            try { orderId = UUID.fromString(orderQuery); } catch (IllegalArgumentException ignored) { /* reference may be an order number */ }
        }
        Pageable pageable=PageRequest.of(page,size,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id")));
        return movements.searchForDistributor(businessId,productId,type,from,until,orderQuery,orderId,pageable)
            .map(InventoryMovementResponse::from);
    }

    @Transactional(readOnly=true)
    public InventoryReconciliationResponse reconcile(UUID userId,UUID productId){
        UUID businessId=businessId(userId);
        Product product=products.findByIdAndDistributorId(productId,businessId).orElseThrow(()->new ResourceNotFoundException("Product not found."));
        Inventory row=inventory.findByProductIdAndDistributorId(productId,businessId).orElseThrow(()->new ResourceNotFoundException("Inventory not found."));
        if(movements==null) return new InventoryReconciliationResponse(productId,product.getName(),row.getOnHandCases(),row.getReservedCases(),row.getAvailableCases(),false,null,null,null,null,null,"Movement ledger is not available.");
        var baseline=movements.findFirstByInventoryIdAndMovementTypeOrderByCreatedAtAscIdAsc(row.getId(),InventoryMovementType.OPENING_BALANCE);
        if(baseline.isEmpty()) return new InventoryReconciliationResponse(productId,product.getName(),row.getOnHandCases(),row.getReservedCases(),row.getAvailableCases(),false,null,null,null,null,null,"No opening balance exists; the ledger cannot yet be reconciled.");
        long ledgerOnHand=baseline.get().getOnHandAfter()+movements.sumOnHandDeltas(row.getId(),InventoryMovementType.OPENING_BALANCE,baseline.get().getCreatedAt());
        long ledgerReserved=baseline.get().getReservedAfter()+movements.sumReservedDeltas(row.getId(),InventoryMovementType.OPENING_BALANCE,baseline.get().getCreatedAt());
        long ledgerAvailable=ledgerOnHand-ledgerReserved;
        boolean discrepancy=ledgerOnHand!=row.getOnHandCases()||ledgerReserved!=row.getReservedCases()||ledgerAvailable!=row.getAvailableCases();
        return new InventoryReconciliationResponse(productId,product.getName(),row.getOnHandCases(),row.getReservedCases(),row.getAvailableCases(),true,ledgerOnHand,ledgerReserved,ledgerAvailable,discrepancy,baseline.get().getCreatedAt(),"Movement coverage begins at the opening snapshot. Earlier adjustment and order history is not reconstructed.");
    }

    private UUID businessId(UUID userId){if(userId==null)throw new AccessDeniedException("Distributor inventory access denied.");Set<UUID> ids=authorization.activeOrderDeskBusinessIds(userId);if(ids.size()!=1)throw new AccessDeniedException("Select a single authorized distributor workspace.");UUID id=ids.iterator().next();authorization.requireOrderDeskAccess(userId,id);return id;}
}
