package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public List<Product> findAllActive() {
        return productRepository.findByActiveTrue();
    }

    public List<Product> findByCategory(long categoryId) {
        return productRepository.findByCategory_Id(categoryId);
    }

    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    public Product save(Product product) {
        return productRepository.save(product);
    }

    public void deleteById(Long id) {
        productRepository.deleteById(id);
    }

    public Page<Product> findWithFilters(String name, Long categoryId, Long brandId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "created_at"));
        return productRepository.findWithFilters(name, categoryId, brandId, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<Product> findByIdWithVariants(Long id) {
        return productRepository.findByIdWithVariants(id);
    }
}
