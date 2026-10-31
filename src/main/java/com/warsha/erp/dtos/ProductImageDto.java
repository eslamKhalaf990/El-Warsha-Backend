package com.warsha.erp.dtos;

import com.warsha.erp.entities.ProductImage;

/** One extra gallery image. {@code id} is used to delete or reorder it. */
public record ProductImageDto(Long id, String imageUrl, int sortOrder) {

    public static ProductImageDto of(ProductImage image) {
        return new ProductImageDto(image.getId(), image.getImageUrl(),
                image.getSortOrder() != null ? image.getSortOrder() : 0);
    }
}
