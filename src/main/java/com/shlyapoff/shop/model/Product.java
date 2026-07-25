package com.shlyapoff.shop.model;

import jakarta.persistence.*;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Data
@EntityListeners(AuditingEntityListener.class)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @PositiveOrZero
    @Column(nullable = false)
    private BigDecimal price;

    private String imageUrl;

    private String nicotineStrength;

    private String volume;

    private String batteryCapacity;

    private String puffCount;

    private String cartridgeVolume;

    private String maxPower;

    private String packageQuantity;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ProductVariant> variants = new ArrayList<>();

    @Column(name = "is_active", nullable = false)
    private Boolean active = true;

    @PositiveOrZero
    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity = 0;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne
    @JoinColumn(name = "brand_id")
    private Brand brand;

    public int getAvailableStockQuantity() {
        if (variants == null || variants.isEmpty()) {
            return stockQuantity == null ? 0 : stockQuantity;
        }
        return variants.stream()
                .map(ProductVariant::getStockQuantity)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }
}
