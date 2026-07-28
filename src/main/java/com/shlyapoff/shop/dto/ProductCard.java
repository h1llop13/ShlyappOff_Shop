package com.shlyapoff.shop.dto;

import com.shlyapoff.shop.model.VariantType;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Minimal immutable representation used by the home page and catalog cards.
 * It deliberately excludes product variants and detail-only fields.
 */
@Value
public class ProductCard {
    Long id;
    String name;
    String description;
    BigDecimal price;
    Integer stockQuantity;
    String imageUrl;
    String imageThumbnailUrl;
    VariantType variantType;

    public boolean isRequiresVariant() {
        return variantType != null && variantType != VariantType.NONE;
    }
}
