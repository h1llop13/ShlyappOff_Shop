package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.model.AdminAuditAction;
import com.shlyapoff.shop.model.ProductVariant;
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

    public List<ProductVariant> findByProductId(Long productId) {
        return productVariantRepository.findByProductId(productId);
    }

    public List<ProductVariant> findInStockByProductId(Long productId) {
        return productVariantRepository.findByProductIdAndInStockTrue(productId);
    }

    @Transactional
    @CacheEvict(cacheNames = "latestProducts", allEntries = true)
    public ProductVariant save(Long productId, String value, Integer stockQuantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Товар не найден"));

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setValue(value);
        variant.setStockQuantity(stockQuantity);

        return productVariantRepository.save(variant);
    }

    @Transactional
    @CacheEvict(cacheNames = "latestProducts", allEntries = true)
    public void deleteById(Long id) {
        productVariantRepository.deleteById(id);
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
        auditLogService.recordChange(actorUsername, AdminAuditAction.VARIANT_STOCK_CHANGED,
                "PRODUCT_VARIANT", variant.getId(), "stockQuantity", previousStock, variant.getStockQuantity());
    }
}
