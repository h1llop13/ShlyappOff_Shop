package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для ProductService.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    @DisplayName("findAllActive возвращает только активные товары")
    void findAllActiveReturnsOnlyActiveProducts() {
        Product active = new Product();
        active.setId(1L);
        active.setActive(true);

        when(productRepository.findByActiveTrue()).thenReturn(List.of(active));

        List<Product> result = productService.findAllActive();

        assertThat(result).hasSize(1).containsExactly(active);
        verify(productRepository).findByActiveTrue();
    }

    @Test
    @DisplayName("findByCategory делегирует поиск в репозиторий по id категории")
    void findByCategoryDelegatesToRepository() {
        Product product = new Product();
        product.setId(1L);
        when(productRepository.findByCategory_Id(7L)).thenReturn(List.of(product));

        List<Product> result = productService.findByCategory(7L);

        assertThat(result).containsExactly(product);
    }

    @Test
    @DisplayName("findById возвращает пустой Optional, если товар не найден")
    void findByIdReturnsEmptyWhenNotFound() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<Product> result = productService.findById(999L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("save сохраняет товар через репозиторий и возвращает результат")
    void saveDelegatesToRepository() {
        Product product = new Product();
        product.setName("Новый товар");
        Product saved = new Product();
        saved.setId(1L);
        saved.setName("Новый товар");

        when(productRepository.save(product)).thenReturn(saved);

        Product result = productService.save(product);

        assertThat(result.getId()).isEqualTo(1L);
        verify(productRepository).save(product);
    }

    @Test
    @DisplayName("deleteById вызывает удаление в репозитории")
    void deleteByIdDelegatesToRepository() {
        productService.deleteById(5L);

        verify(productRepository).deleteById(5L);
    }

    @Test
    @DisplayName("findWithFilters строит Pageable с сортировкой по created_at и передаёт фильтры")
    void findWithFiltersBuildsPageableAndDelegates() {
        Product product = new Product();
        Page<Product> page = new PageImpl<>(List.of(product));

        when(productRepository.findWithFilters(eq("vape"), eq(2L), eq(3L), any(Pageable.class)))
                .thenReturn(page);

        Page<Product> result = productService.findWithFilters("vape", 2L, 3L, 0, 10);

        assertThat(result.getContent()).containsExactly(product);
        verify(productRepository).findWithFilters(eq("vape"), eq(2L), eq(3L), any(Pageable.class));
    }

    @Test
    @DisplayName("findByIdWithVariants возвращает товар вместе с вариантами")
    void findByIdWithVariantsDelegatesToRepository() {
        Product product = new Product();
        product.setId(1L);
        when(productRepository.findByIdWithVariants(1L)).thenReturn(Optional.of(product));

        Optional<Product> result = productService.findByIdWithVariants(1L);

        assertThat(result).contains(product);
    }
}