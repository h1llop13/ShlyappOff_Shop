package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.model.ProductVariant;
import com.shlyapoff.shop.repository.ProductRepository;
import com.shlyapoff.shop.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductVariantService {

    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;

    public List<ProductVariant> findByProductId(Long productId) {
        return productVariantRepository.findByProductId(productId);
    }

    public List<ProductVariant> findInStockByProductId(Long productId) {
        return productVariantRepository.findByProductIdAndInStockTrue(productId);
    }

    @Transactional
    public ProductVariant save(Long productId, String value, Boolean inStock) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Товар не найден"));

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setValue(value);
        variant.setInStock(inStock);

        return productVariantRepository.save(variant);
    }

    @Transactional
    public void deleteById(Long id) {
        productVariantRepository.deleteById(id);
    }

    @Transactional
    public void updateStock(Long id, Boolean inStock) {
        ProductVariant variant = productVariantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Вариант не найден"));
        variant.setInStock(inStock);
        productVariantRepository.save(variant);
    }
}
