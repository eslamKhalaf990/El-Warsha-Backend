package com.warsha.erp.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * The "product" part of POST /products and PUT /products/{id} (multipart, with an
 * optional "image" part). Prices are in EGP; {@code discount} is taken off each unit.
 */
@Getter
@Setter
public class ProductRequest {

    @NotBlank(message = "Enter the product name.")
    @Size(max = 100, message = "Name can be at most 100 characters.")
    private String name;

    @Size(max = 255, message = "Arabic name can be at most 255 characters.")
    private String nameAr;

    @Size(max = 500, message = "Description can be at most 500 characters.")
    private String description;

    @Size(max = 4000, message = "Arabic description can be at most 4000 characters.")
    private String descriptionAr;

    @NotNull(message = "Enter the buying price.")
    @PositiveOrZero(message = "Buying price can't be negative.")
    private Double buyingPrice;

    @NotNull(message = "Enter the selling price.")
    @PositiveOrZero(message = "Selling price can't be negative.")
    private Double sellingPrice;

    @PositiveOrZero(message = "Discount can't be negative.")
    private Double discount;

    /** Ignored when the product has sizes/colors: their stock adds up to it. */
    @Min(value = 0, message = "Stock can't be negative.")
    @Max(value = 1_000_000, message = "Stock looks too large.")
    private Integer quantity;

    @NotNull(message = "Choose a category.")
    private Long categoryId;
}
