package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Brand;
import com.shlyapoff.shop.repository.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;

    @Cacheable("brands")
    public List<Brand> findAll() {
        return brandRepository.findAll();
    }

    public Optional<Brand> findById(Long id) {
        return brandRepository.findById(id);
    }

    @CacheEvict(cacheNames = {"brands", "latestProducts"}, allEntries = true)
    public Brand save(Brand brand) {
        return brandRepository.save(brand);
    }

    @CacheEvict(cacheNames = {"brands", "latestProducts"}, allEntries = true)
    public void deleteById(Long id) {
        brandRepository.deleteById(id);
    }
}
