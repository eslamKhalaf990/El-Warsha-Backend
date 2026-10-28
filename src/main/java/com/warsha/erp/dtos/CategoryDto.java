package com.warsha.erp.dtos;

import com.warsha.erp.entities.Category;

/**
 * A category as the ERP and the store see it. {@code productCount} counts the
 * products that are not deleted.
 */
public record CategoryDto(Long categoryId, String name, String description, String imageUrl, long productCount) {

    public static CategoryDto of(Category c, long productCount) {
        return new CategoryDto(c.getCategoryId(), c.getName(), c.getDescription(), c.getImageUrl(), productCount);
    }
}
