package com.warsha.erp.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductDTO {
    private Long id;
    private String name;
    private String nameAr;
    private String deleted;
    private LocalDateTime deletedAt;
    private String description;
    private String descriptionAr;
    private Double buyingPrice;
    private Double sellingPrice;
    private Double discount;
    private Double totalPrice;
    private String imageUrl;
    private String sku;
    private String quantity;
    private String categoryName;
    private Long categoryId;
    private boolean hasVariants;
    /** Active sizes/colors; empty for products without variants. */
    private List<ProductVariantDto> variants = List.of();
    /** Extra gallery images after the cover ({@code imageUrl}), in display order. */
    private List<ProductImageDto> images = List.of();
}
