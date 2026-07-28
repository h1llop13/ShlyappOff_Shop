package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Category;
import com.shlyapoff.shop.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Cacheable("categories")
    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    public Optional<Category> findById(Long id) {
        return categoryRepository.findById(id);
    }

    @CacheEvict(cacheNames = {"categories", "latestProducts"}, allEntries = true)
    public Category save(Category category) {
        return categoryRepository.save(category);
    }

    @CacheEvict(cacheNames = {"categories", "latestProducts"}, allEntries = true)
    public void deleteById(Long id) {
        categoryRepository.deleteById(id);
    }
}
