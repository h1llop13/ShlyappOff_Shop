package com.shlyapoff.shop.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_variants")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "variant_value", nullable = false)
    private String value;

    @Column(name = "in_stock", nullable = false)
    private Boolean inStock = true;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity = 0;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // Явно добавляем сеттер для inStock (на случай, если Lombok не сгенерирует)
    public void setInStock(Boolean inStock) {
        this.inStock = inStock;
    }

    public Boolean getInStock() {
        return inStock;
    }

    public void setStockQuantity(Integer stockQuantity) {
        int normalizedQuantity = stockQuantity == null ? 0 : stockQuantity;
        if (normalizedQuantity < 0) {
            throw new IllegalArgumentException("Количество варианта не может быть отрицательным");
        }
        this.stockQuantity = normalizedQuantity;
        this.inStock = normalizedQuantity > 0;
    }
}
