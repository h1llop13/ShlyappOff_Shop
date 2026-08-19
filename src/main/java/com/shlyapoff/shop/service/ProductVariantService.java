package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.model.AdminAuditAction;
import com.shlyapoff.shop.model.ProductVariant;
import com.shlyapoff.shop.model.InventoryMovementType;
import com.shlyapoff.shop.repository.ProductRepository;
import com.shlyapoff.shop.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductVariantService {

    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;
    private final AdminAuditLogService auditLogService;
    private final InventoryService inventoryService;

    public List<ProductVariant> findByProductId(Long productId) {
        return productVariantRepository.findByProductId(productId);
    }

    public List<ProductVariant> findInStockByProductId(Long productId) {
        return productVariantRepository.findByProductIdAndInStockTrue(productId);
    }

    @Transactional
    @CacheEvict(cacheNames = "latestProducts", allEntries = true)
    public ProductVariant save(Long productId, String value, Integer stockQuantity) {
        return save(productId, value, stockQuantity, "system");
    }

    @Transactional
    @CacheEvict(cacheNames = "latestProducts", allEntries = true)
    public ProductVariant save(Long productId, String value, Integer stockQuantity, String actorUsername) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Товар не найден"));

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setValue(value);
        variant.setStockQuantity(stockQuantity);

        ProductVariant saved = productVariantRepository.save(variant);
        inventoryService.record(product, saved, InventoryMovementType.INITIAL_STOCK,
                0, saved.getStockQuantity(), "Создание варианта", actorUsername, null, null);
        return saved;
    }

    @Transactional
    @CacheEvict(cacheNames = "latestProducts", allEntries = true)
    public void deleteById(Long id) {
        ProductVariant variant = productVariantRepository.findByIdWithProduct(id)
                .orElseThrow(() -> new IllegalArgumentException("Вариант не найден: " + id));
        int before = variant.getStockQuantity() == null ? 0 : variant.getStockQuantity();
        inventoryService.record(variant.getProduct(), null, InventoryMovementType.MANUAL_ADJUSTMENT,
                before, 0, "Удаление варианта: " + variant.getValue(), "admin", null, null);
        productVariantRepository.delete(variant);
    }

    @Transactional
    @CacheEvict(cacheNames = "latestProducts", allEntries = true)
    public void updateStockQuantity(Long id, Integer stockQuantity) {
        updateStockQuantity(id, stockQuantity, "system");
    }

    @Transactional
    @CacheEvict(cacheNames = "latestProducts", allEntries = true)
    public void updateStockQuantity(Long id, Integer stockQuantity, String actorUsername) {
        ProductVariant variant = productVariantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Вариант не найден"));
        Integer previousStock = variant.getStockQuantity();
        variant.setStockQuantity(stockQuantity);
        productVariantRepository.save(variant);
        inventoryService.record(variant.getProduct(), variant, InventoryMovementType.MANUAL_ADJUSTMENT,
                previousStock == null ? 0 : previousStock, variant.getStockQuantity(),
                "Изменение в карточке варианта", actorUsername, null, null);
        auditLogService.recordChange(actorUsername, AdminAuditAction.VARIANT_STOCK_CHANGED,
                "PRODUCT_VARIANT", variant.getId(), "stockQuantity", previousStock, variant.getStockQuantity());
    }
}
