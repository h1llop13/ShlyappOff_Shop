package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.model.Promotion;
import com.shlyapoff.shop.model.PublicationStatus;
import com.shlyapoff.shop.repository.ProductRepository;
import com.shlyapoff.shop.repository.PromotionRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PublicationServiceTest {
    @Test
    void productDraftIsNotActiveAndFutureScheduleStaysInactive() {
        ProductService service = new ProductService(mock(ProductRepository.class), mock(AdminAuditLogService.class));
        Product product = new Product();
        product.setPublicationStatus(PublicationStatus.DRAFT);
        service.applyPublicationState(product);
        assertThat(product.getActive()).isFalse();

        product.setPublicationStatus(PublicationStatus.SCHEDULED);
        product.setPublishAt(LocalDateTime.now().plusHours(1));
        service.applyPublicationState(product);
        assertThat(product.getActive()).isFalse();
        assertThat(product.getPublicationStatus()).isEqualTo(PublicationStatus.SCHEDULED);
    }

    @Test
    void pastSchedulePublishesAndMissingDateIsRejected() {
        PromotionService service = new PromotionService(mock(PromotionRepository.class));
        Promotion promotion = new Promotion();
        promotion.setPublicationStatus(PublicationStatus.SCHEDULED);
        promotion.setPublishAt(LocalDateTime.now().minusMinutes(1));
        service.applyPublicationState(promotion);
        assertThat(promotion.getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(promotion.getActive()).isTrue();

        ProductService productService = new ProductService(mock(ProductRepository.class), mock(AdminAuditLogService.class));
        Product invalid = new Product();
        invalid.setPublicationStatus(PublicationStatus.SCHEDULED);
        invalid.setPublishAt(null);
        assertThatThrownBy(() -> productService.applyPublicationState(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("дату и время");
    }
}
