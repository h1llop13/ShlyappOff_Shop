package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.AdminAuditAction;
import com.shlyapoff.shop.model.ProductVariant;
import com.shlyapoff.shop.repository.ProductRepository;
import com.shlyapoff.shop.repository.ProductVariantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductVariantServiceTest {

    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private ProductRepository productRepository;
    @Mock private AdminAuditLogService auditLogService;
    @Mock private InventoryService inventoryService;
    @InjectMocks private ProductVariantService productVariantService;

    @Test
    void recordsAdministratorWhenVariantStockChanges() {
        ProductVariant variant = new ProductVariant();
        variant.setId(7L);
        variant.setStockQuantity(3);
        when(productVariantRepository.findById(7L)).thenReturn(Optional.of(variant));

        productVariantService.updateStockQuantity(7L, 8, "stock-admin");

        assertThat(variant.getStockQuantity()).isEqualTo(8);
        verify(productVariantRepository).save(variant);
        verify(auditLogService).recordChange(
                "stock-admin", AdminAuditAction.VARIANT_STOCK_CHANGED,
                "PRODUCT_VARIANT", 7L, "stockQuantity", 3, 8);
    }
}
