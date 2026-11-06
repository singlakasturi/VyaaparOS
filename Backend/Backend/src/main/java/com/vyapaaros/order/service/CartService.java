package com.vyapaaros.order.service;
import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.catalog.repository.ProductRepository;
import com.vyapaaros.common.exception.*;
import com.vyapaaros.inventory.repository.InventoryRepository;
import com.vyapaaros.order.dto.*;
import com.vyapaaros.order.entity.*;
import com.vyapaaros.order.repository.*;
import com.vyapaaros.pricing.dto.ResolvedPrice;
import com.vyapaaros.pricing.service.PriceResolutionService;
import com.vyapaaros.retailer.entity.Retailer;
import com.vyapaaros.retailer.entity.RetailerStatus;
import com.vyapaaros.retailer.repository.RetailerRepository;
import com.vyapaaros.schemes.dto.SchemeSummary;
import com.vyapaaros.schemes.dto.SchemeEvaluation;
import com.vyapaaros.schemes.service.SchemeResolutionService;
import com.vyapaaros.user.entity.User;
import com.vyapaaros.user.repository.UserRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {
 private final CartRepository carts; private final CartItemRepository items; private final ProductRepository products;
 private final RetailerRepository retailers; private final UserRepository users; private final OrderScopeService scope;
 private final InventoryRepository inventory; private final PriceResolutionService prices; private final SchemeResolutionService schemes;
 public CartService(CartRepository c,CartItemRepository i,ProductRepository p,RetailerRepository r,UserRepository u,OrderScopeService s,InventoryRepository inv,PriceResolutionService pr,SchemeResolutionService sc){carts=c;items=i;products=p;retailers=r;users=u;scope=s;inventory=inv;prices=pr;schemes=sc;}
 @Transactional public CartResponse get(Authentication a,UUID retailerId){
  UUID uid=scope.userId(a);scope.require(uid,retailerId,a);activeRetailer(retailerId);
  Cart c=carts.findActive(uid,retailerId).orElseGet(()->create(uid,retailerId));
  return response(c,a,retailerId);
 }
 @Transactional public CartResponse upsert(Authentication a,CartItemRequest r){
  UUID uid=scope.userId(a);scope.require(uid,r.retailerId(),a); activeRetailer(r.retailerId()); UUID distributorId=scope.currentDistributorForRetailer(r.retailerId()).getId(); Product p=active(r.productId(),distributorId);
  validateStock(a,r.retailerId(),p,r.paidCases());
  Cart c=carts.findActiveForUpdate(uid,r.retailerId()).orElseGet(()->create(uid,r.retailerId()));
  CartItem item=items.findByCartIdAndProductId(c.getId(),p.getId()).orElseGet(()->{CartItem x=new CartItem();x.setProduct(p);x.setCreatedAt(Instant.now());x.setPosition(c.getItems().stream().mapToInt(CartItem::getPosition).max().orElse(-1)+1);c.addItem(x);return x;});
  item.setPaidCases(r.paidCases());item.setUpdatedAt(Instant.now());c.setUpdatedAt(Instant.now());carts.save(c);return response(c,a,r.retailerId());
 }
 @Transactional public CartResponse update(Authentication a,UUID itemId,CartQuantityRequest r){
  UUID uid=scope.userId(a);CartItem item=items.findById(itemId).orElseThrow(()->new ResourceNotFoundException("Cart item not found."));
  scope.require(uid,item.getCart().getRetailer().getId(),a);activeRetailer(item.getCart().getRetailer().getId());requireCartOwner(uid,item.getCart(),a);
  validateStock(a,item.getCart().getRetailer().getId(),item.getProduct(),r.paidCases());
  item.setPaidCases(r.paidCases());item.setUpdatedAt(Instant.now());items.save(item);return response(item.getCart(),a,item.getCart().getRetailer().getId());
 }
 @Transactional public void remove(Authentication a,UUID itemId){CartItem i=items.findById(itemId).orElseThrow(()->new ResourceNotFoundException("Cart item not found."));UUID uid=scope.userId(a);scope.require(uid,i.getCart().getRetailer().getId(),a);requireCartOwner(uid,i.getCart(),a);items.delete(i);}
 @Transactional public void clear(Authentication a,UUID retailerId){UUID uid=scope.userId(a);scope.require(uid,retailerId,a);activeRetailer(retailerId);carts.findActive(uid,retailerId).ifPresent(c->{c.getItems().clear();c.setUpdatedAt(Instant.now());carts.save(c);});}
 private Cart create(UUID uid,UUID rid){Retailer r=retailers.findById(rid).orElseThrow(()->new ResourceNotFoundException("Retailer not found."));User u=users.findById(uid).orElseThrow(()->new ResourceNotFoundException("User not found."));Cart c=new Cart();c.setRetailer(r);c.setCreatedBy(u);c.setStatus(CartStatus.ACTIVE);c.setCreatedAt(Instant.now());c.setUpdatedAt(Instant.now());return carts.save(c);}
 private Product active(UUID id,UUID distributorId){return products.findByIdAndDistributorIdAndActiveTrue(id,distributorId).filter(p->p.getCategory().isActive()).orElseThrow(()->new ResourceNotFoundException("Active product not found."));}
 private void validateStock(Authentication auth,UUID retailerId,Product product,int paidCases){
  var price=prices.resolve(auth,retailerId,List.of(product.getId())).getOrDefault(product.getId(),ResolvedPrice.unavailable());
  var scheme=schemes.evaluate(product.getId(),retailerId,paidCases,price,auth);
  int requested=paidCases+(scheme.priceAvailable()?scheme.freeCases():0);
  UUID distributorId=scope.currentDistributorForRetailer(retailerId).getId();
  int available=inventory.findByProductIdAndDistributorId(product.getId(),distributorId).map(row->row.getAvailableCases()).orElse(0);
  if(requested>available)throw new InsufficientStockException(List.of(new InsufficientStockException.StockIssue(product.getSku(),product.getName(),requested,available)));
 }
 private void requireCartOwner(UUID userId,Cart cart,Authentication authentication){if(cart.getCreatedBy()==null||!cart.getCreatedBy().getId().equals(userId))throw new org.springframework.security.access.AccessDeniedException("Cart access denied.");}
 private void activeRetailer(UUID id){if(retailers.findById(id).map(r->r.getStatus()==RetailerStatus.ACTIVE).orElse(false)==false)throw new ResourceConflictException("Retailer is not active.");}
 private CartResponse response(Cart c,Authentication a,UUID rid){List<UUID> ids=c.getItems().stream().map(i->i.getProduct().getId()).toList();Map<UUID,ResolvedPrice> ps=prices.resolve(a,rid,ids);Map<UUID,SchemeSummary> ss=schemes.summaries(a,rid,ids);UUID distributorId=scope.currentDistributorForRetailer(rid).getId();Map<UUID,Integer> stock=new HashMap<>();inventory.findByProductIdInAndDistributorId(ids,distributorId).forEach(i->stock.put(i.getProduct().getId(),i.getAvailableCases()));return CartResponse.from(c,c.getItems().stream().map(i->{ResolvedPrice p=ps.getOrDefault(i.getProduct().getId(),ResolvedPrice.unavailable());SchemeEvaluation evaluation=schemes.evaluate(i.getProduct().getId(),rid,i.getPaidCases(),p,a);return CartItemResponse.from(i,stock.getOrDefault(i.getProduct().getId(),0),p,ss.getOrDefault(i.getProduct().getId(),SchemeSummary.unavailable()),evaluation.freeCases(),evaluation.baseAmount(),evaluation.discountAmount(),evaluation.finalAmount());}).toList());}
}
