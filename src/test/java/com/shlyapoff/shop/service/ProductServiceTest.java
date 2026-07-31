package com.shlyapoff.shop.service;

import com.shlyapoff.shop.dto.ProductCard;
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
    @DisplayName("findLatestActive возвращает до двенадцати последних активных товаров")
    void findLatestActiveReturnsProductsFromRepository() {
        ProductCard active = new ProductCard(1L, "Товар", null, null, 1, null, null, null);

        when(productRepository.findLatestActiveCards(any(Pageable.class))).thenReturn(List.of(active));

        List<ProductCard> result = productService.findLatestActive();

        assertThat(result).hasSize(1).containsExactly(active);
        verify(productRepository).findLatestActiveCards(any(Pageable.class));
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
    void deleteByIdDeactivatesProduct() {
        Product product = new Product();
        product.setActive(true);
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));

        boolean deleted = productService.deleteById(5L);

        assertThat(deleted).isTrue();
        assertThat(product.getActive()).isFalse();
        verify(productRepository).findById(5L);
        verify(productRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("findWithFilters строит Pageable с сортировкой по created_at и передаёт фильтры")
    void findWithFiltersBuildsPageableAndDelegates() {
        ProductCard product = new ProductCard(1L, "Товар", null, null, 1, null, null, null);
        Page<ProductCard> page = new PageImpl<>(List.of(product));

        when(productRepository.findCardsByNameAndCategoryAndBrand(
                eq("vape"), eq(2L), eq(3L), any(Pageable.class)))
                .thenReturn(page);

        Page<ProductCard> result = productService.findWithFilters("vape", 2L, 3L, 0, 10);

        assertThat(result.getContent()).containsExactly(product);
        verify(productRepository).findCardsByNameAndCategoryAndBrand(
                eq("vape"), eq(2L), eq(3L), any(Pageable.class));
    }

    @Test
    @DisplayName("Расширенный каталог передаёт пустой поиск строкой, а не null")
    void extendedFiltersUseEmptyStringForMissingSearch() {
        when(productRepository.findCardsWithFilters(
                anyString(), isNull(), isNull(), isNull(), isNull(), eq(false), any(Pageable.class)))
                .thenReturn(Page.empty());

        productService.findWithFilters(null, null, null, null, null,
                false, "newest", 0, 12);

        verify(productRepository).findCardsWithFilters(
                eq(""), isNull(), isNull(), isNull(), isNull(), eq(false), any(Pageable.class));
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
