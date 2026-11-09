package com.vyapaaros.distributor.service;

import com.vyapaaros.catalog.dto.*;
import com.vyapaaros.catalog.entity.*;
import com.vyapaaros.catalog.repository.*;
import com.vyapaaros.common.exception.ResourceConflictException;
import com.vyapaaros.common.exception.ResourceNotFoundException;
import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.distributor.repository.DistributorBusinessRepository;
import com.vyapaaros.inventory.entity.Inventory;
import com.vyapaaros.inventory.repository.InventoryRepository;
import com.vyapaaros.inventory.service.InventoryMovementService;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DistributorCatalogManagementService {
    private static final Pattern SLUG=Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private final DistributorAuthorizationService authorization;
    private final DistributorBusinessRepository businesses;
    private final ProductCategoryRepository categories;
    private final ProductRepository products;
    private final InventoryRepository inventory;
    private final InventoryMovementService movementService;

    public DistributorCatalogManagementService(DistributorAuthorizationService authorization,DistributorBusinessRepository businesses,
      ProductCategoryRepository categories,ProductRepository products,InventoryRepository inventory){
        this(authorization,businesses,categories,products,inventory,null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DistributorCatalogManagementService(DistributorAuthorizationService authorization,DistributorBusinessRepository businesses,
      ProductCategoryRepository categories,ProductRepository products,InventoryRepository inventory,InventoryMovementService movementService){
        this.authorization=authorization;this.businesses=businesses;this.categories=categories;this.products=products;this.inventory=inventory;this.movementService=movementService;
    }

    @Transactional(readOnly=true)
    public List<CategoryResponse> categories(UUID userId){UUID businessId=businessId(userId);return categories.findByDistributorIdAndActiveTrueOrderByNameAsc(businessId).stream().map(CategoryResponse::from).toList();}

    @Transactional
    public CategoryResponse createCategory(UUID userId,String name,String slug,String description){
        UUID businessId=businessId(userId);String normalizedName=required(name,"Category name is required.");String normalizedSlug=required(slug,"Category slug is required.").toLowerCase(Locale.ROOT);
        if(!SLUG.matcher(normalizedSlug).matches())throw new IllegalArgumentException("Slug may contain lowercase letters, numbers, and single hyphens.");
        if(categories.existsByDistributorIdAndSlugIgnoreCase(businessId,normalizedSlug))throw new ResourceConflictException("This category slug is already in use.");
        ProductCategory c=new ProductCategory();c.setDistributor(businesses.getReferenceById(businessId));c.setName(normalizedName);c.setSlug(normalizedSlug);c.setDescription(normalize(description));c.setActive(true);c.setCreatedAt(Instant.now());c.setUpdatedAt(Instant.now());
        try{return CategoryResponse.from(categories.saveAndFlush(c));}catch(DataIntegrityViolationException ex){throw new ResourceConflictException("This category slug is already in use.");}
    }

    @Transactional(readOnly=true)
    public List<ManagedProductResponse> listProducts(UUID userId,String query,Boolean active){
        UUID businessId=businessId(userId);String q=normalize(query);
        List<Product> rows=products.findManagedByDistributor(businessId,q==null?null:q.trim(),active);
        Map<UUID,Integer> stocks=rows.isEmpty()?Map.of():inventory.findByProductIdInAndDistributorId(rows.stream().map(Product::getId).toList(),businessId).stream().collect(java.util.stream.Collectors.toMap(i->i.getProduct().getId(),Inventory::getAvailableCases));
        return rows.stream().map(p->ManagedProductResponse.from(p,stocks.getOrDefault(p.getId(),0))).toList();
    }

    @Transactional(readOnly=true)
    public ManagedProductResponse getProduct(UUID userId,UUID productId){UUID businessId=businessId(userId);Product p=product(productId,businessId);int stock=inventory.findByProductIdAndDistributorId(productId,businessId).map(Inventory::getAvailableCases).orElse(0);return ManagedProductResponse.from(p,stock);}

    @Transactional
    public ManagedProductResponse createProduct(UUID userId,ProductUpsertRequest request){
        UUID businessId=businessId(userId);String sku=required(request.sku(),"SKU is required.").trim();
        if(products.existsByDistributorIdAndSkuIgnoreCase(businessId,sku))throw new ResourceConflictException("This SKU is already in use by your distributor.");
        DistributorBusiness business=businesses.getReferenceById(businessId);ProductCategory category=category(request.categoryId(),businessId);
        Instant now=Instant.now();Product p=new Product();p.setDistributor(business);apply(p,request,sku,category);p.setActive(true);p.setCreatedAt(now);p.setUpdatedAt(now);
        try{
            Product saved=products.saveAndFlush(p);Inventory row=new Inventory();row.setDistributor(business);row.setProduct(saved);row.setAvailableCases(0);row.setOnHandCases(0);row.setReservedCases(0);row.setLowStockThreshold(0);row.setUpdatedAt(now);Inventory savedInventory=inventory.saveAndFlush(row);
            if(movementService!=null)movementService.openingBalance(savedInventory,"Initial product stock baseline.");
            return ManagedProductResponse.from(saved,0);
        }catch(DataIntegrityViolationException ex){throw new ResourceConflictException("SKU or product details conflict with existing data.");}
    }

    @Transactional
    public ManagedProductResponse updateProduct(UUID userId,UUID productId,ProductUpsertRequest request){
        UUID businessId=businessId(userId);Product p=product(productId,businessId);String sku=required(request.sku(),"SKU is required.").trim();
        if(products.existsByDistributorIdAndSkuIgnoreCaseAndIdNot(businessId,sku,productId))throw new ResourceConflictException("This SKU is already in use by your distributor.");
        ProductCategory category=category(request.categoryId(),businessId);apply(p,request,sku,category);p.setUpdatedAt(Instant.now());
        try{products.saveAndFlush(p);}catch(DataIntegrityViolationException ex){throw new ResourceConflictException("SKU or product details conflict with existing data.");}
        return getProductInTransaction(p,businessId);
    }

    @Transactional
    public ManagedProductResponse setProductActive(UUID userId,UUID productId,boolean active){UUID businessId=businessId(userId);Product p=product(productId,businessId);p.setActive(active);p.setUpdatedAt(Instant.now());products.save(p);return getProductInTransaction(p,businessId);}

    private ManagedProductResponse getProductInTransaction(Product p,UUID businessId){int stock=inventory.findByProductIdAndDistributorId(p.getId(),businessId).map(Inventory::getAvailableCases).orElse(0);return ManagedProductResponse.from(p,stock);}
    private Product product(UUID productId,UUID businessId){return products.findByIdAndDistributorId(productId,businessId).orElseThrow(()->new ResourceNotFoundException("Product not found."));}
    private ProductCategory category(UUID categoryId,UUID businessId){return categories.findByIdAndDistributorIdAndActiveTrue(categoryId,businessId).orElseThrow(()->new ResourceNotFoundException("Category not found for this distributor."));}
    private void apply(Product p,ProductUpsertRequest r,String sku,ProductCategory category){p.setSku(sku);p.setName(required(r.name(),"Product name is required.").trim());p.setDescription(normalize(r.description()));p.setBrand(normalize(r.brand()));p.setCategory(category);p.setUnitName(required(r.unitName(),"Unit name is required.").trim());p.setUnitQuantity(r.unitQuantity());p.setUnitsPerCase(r.unitsPerCase());p.setCaseDescription(normalize(r.caseDescription()));}
    private UUID businessId(UUID userId){if(userId==null)throw denied();Set<UUID> ids=authorization.activeOrderDeskBusinessIds(userId);if(ids.size()!=1)throw new AccessDeniedException("Select a single authorized distributor workspace.");UUID id=ids.iterator().next();authorization.requireOrderDeskAccess(userId,id);return id;}
    private AccessDeniedException denied(){return new AccessDeniedException("Distributor catalog access denied.");}
    private String required(String value,String message){if(value==null||value.isBlank())throw new IllegalArgumentException(message);return value;}
    private String normalize(String value){return value==null||value.isBlank()?null:value.trim();}
}
