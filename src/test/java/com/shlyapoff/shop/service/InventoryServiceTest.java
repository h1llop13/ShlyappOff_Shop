package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.*;
import com.shlyapoff.shop.repository.InventoryMovementRepository;
import com.shlyapoff.shop.repository.ProductRepository;
import com.shlyapoff.shop.repository.ProductVariantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {
    @Mock ProductRepository productRepository;
    @Mock ProductVariantRepository variantRepository;
    @Mock InventoryMovementRepository movementRepository;
    @Mock AdminAuditLogService auditLogService;
    @InjectMocks InventoryService inventoryService;

    @Test
    void quickEditUpdatesPriceThresholdAndWritesMovement() {
        Product product = new Product();
        product.setId(10L);
        product.setPrice(new BigDecimal("100.00"));
        product.setStockQuantity(8);
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(product));

        inventoryService.quickEditProduct(10L, new BigDecimal("125.50"), 3, 4,
                "Инвентаризация", "admin");

        assertThat(product.getPrice()).isEqualByComparingTo("125.50");
        assertThat(product.getStockQuantity()).isEqualTo(3);
        assertThat(product.getLowStockThreshold()).isEqualTo(4);
        ArgumentCaptor<InventoryMovement> movement = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movementRepository).save(movement.capture());
        assertThat(movement.getValue().getQuantityBefore()).isEqualTo(8);
        assertThat(movement.getValue().getQuantityAfter()).isEqualTo(3);
        assertThat(movement.getValue().getQuantityDelta()).isEqualTo(-5);
        assertThat(movement.getValue().getReason()).isEqualTo("Инвентаризация");
    }

    @Test
    void alertCountersUseTotalVariantStock() {
        Product low = new Product();
        low.setLowStockThreshold(5);
        ProductVariant lowVariant = new ProductVariant();
        lowVariant.setStockQuantity(2);
        low.setVariants(List.of(lowVariant));

        Product empty = new Product();
        empty.setStockQuantity(0);

        assertThat(inventoryService.lowStockCount(List.of(low, empty))).isEqualTo(1);
        assertThat(inventoryService.outOfStockCount(List.of(low, empty))).isEqualTo(1);
    }
}
