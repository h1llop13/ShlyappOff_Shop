package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.*;
import com.shlyapoff.shop.repository.InventoryMovementRepository;
import com.shlyapoff.shop.repository.ProductRepository;
import com.shlyapoff.shop.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryMovementRepository movementRepository;
    private final AdminAuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<Product> findInventory() {
        return productRepository.findAllForInventory();
    }

    @Transactional(readOnly = true)
    public Page<InventoryMovement> findMovements(int page) {
        return movementRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(Math.max(page, 0), 50));
    }

    @Transactional
    @CacheEvict(cacheNames = "latestProducts", allEntries = true)
    public Product adjustProduct(Long productId, int quantity, int lowStockThreshold,
                                 InventoryMovementType type, String reason, String actor) {
        requireNonNegative(quantity, "Остаток");
        requireNonNegative(lowStockThreshold, "Порог низкого остатка");
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new IllegalArgumentException("Товар не найден: " + productId));
        int before = value(product.getStockQuantity());
        product.setStockQuantity(quantity);
        product.setLowStockThreshold(lowStockThreshold);
        record(product, null, type, before, quantity, reason, actor, null, null);
        return product;
    }

    @Transactional
    @CacheEvict(cacheNames = "latestProducts", allEntries = true)
    public Product quickEditProduct(Long productId, BigDecimal price, int quantity, int lowStockThreshold,
                                    String reason, String actor) {
        if (price == null || price.signum() < 0) throw new IllegalArgumentException("Цена не может быть отрицательной");
        requireNonNegative(quantity, "Остаток");
        requireNonNegative(lowStockThreshold, "Порог низкого остатка");
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new IllegalArgumentException("Товар не найден: " + productId));
        BigDecimal previousPrice = product.getPrice();
        int before = value(product.getStockQuantity());
        product.setPrice(price);
        product.setStockQuantity(quantity);
        product.setLowStockThreshold(lowStockThreshold);
        auditLogService.recordChange(actor, AdminAuditAction.PRODUCT_PRICE_CHANGED,
                "PRODUCT", product.getId(), "price", previousPrice, price);
        auditLogService.recordChange(actor, AdminAuditAction.PRODUCT_STOCK_CHANGED,
                "PRODUCT", product.getId(), "stockQuantity", before, quantity);
        record(product, null, InventoryMovementType.MANUAL_ADJUSTMENT, before, quantity, reason, actor, null, null);
        return product;
    }

    @Transactional
    @CacheEvict(cacheNames = "latestProducts", allEntries = true)
    public ProductVariant adjustVariant(Long variantId, int quantity, InventoryMovementType type,
                                        String reason, String actor) {
        requireNonNegative(quantity, "Остаток");
        ProductVariant variant = productVariantRepository.findByIdForUpdate(variantId)
                .orElseThrow(() -> new IllegalArgumentException("Вариант не найден: " + variantId));
        int before = value(variant.getStockQuantity());
        variant.setStockQuantity(quantity);
        record(variant.getProduct(), variant, type, before, quantity, reason, actor, null, null);
        return variant;
    }

    @Transactional
    public void record(Product product, ProductVariant variant, InventoryMovementType type,
                       int before, int after, String reason, String actor,
                       String referenceType, Long referenceId) {
        if (before == after) return;
        InventoryMovement movement = new InventoryMovement();
        movement.setProduct(product);
        movement.setProductVariant(variant);
        movement.setMovementType(type);
        movement.setQuantityBefore(before);
        movement.setQuantityAfter(after);
        movement.setQuantityDelta(after - before);
        movement.setReason(normalize(reason));
        movement.setActorUsername(normalizeActor(actor));
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movementRepository.save(movement);
    }

    public long outOfStockCount(List<Product> products) {
        return products.stream().filter(Product::isOutOfStock).count();
    }

    public long lowStockCount(List<Product> products) {
        return products.stream().filter(Product::isLowStock).count();
    }

    private static int value(Integer quantity) {
        return quantity == null ? 0 : quantity;
    }

    private static void requireNonNegative(int value, String field) {
        if (value < 0) throw new IllegalArgumentException(field + " не может быть отрицательным");
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }

    private static String normalizeActor(String actor) {
        return actor == null || actor.isBlank() ? "system" : actor.trim();
    }
}
