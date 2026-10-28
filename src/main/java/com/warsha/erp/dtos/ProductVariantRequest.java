package com.warsha.erp.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Body of POST / PUT /products/{id}/variants. Size or color (or both) is required. */
@Getter
@Setter
public class ProductVariantRequest {

    @Size(max = 50, message = "Size can be at most 50 characters.")
    private String size;

    @Size(max = 50, message = "Color can be at most 50 characters.")
    private String color;

    @Size(max = 50, message = "Arabic color can be at most 50 characters.")
    private String colorAr;

    @Pattern(regexp = "^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$", message = "Color code must look like #1A1A1A.")
    private String colorHex;

    @Size(max = 100, message = "SKU can be at most 100 characters.")
    private String sku;

    /** Leave empty to sell at the product's price. */
    @PositiveOrZero(message = "Price can't be negative.")
    private Double sellingPrice;

    @NotNull(message = "Enter the stock quantity.")
    @Min(value = 0, message = "Stock can't be negative.")
    private Integer quantity;

    private String imageUrl;
}
