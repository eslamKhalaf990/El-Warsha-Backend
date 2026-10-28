package com.warsha.erp.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** One size/color combination of a product, with its own stock. */
@Setter
@Getter
@Entity
@Table(name = "ProductVariants")
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "VariantID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ProductID", nullable = false)
    private Product product;

    @Column(name = "Size")
    private String size;

    @Column(name = "Color")
    private String color;

    @Column(name = "ColorAr")
    private String colorAr;

    @Column(name = "ColorHex")
    private String colorHex;

    @Column(name = "SKU")
    private String sku;

    // Null means the variant sells at the product's price
    @Column(name = "SellingPrice")
    private Double sellingPrice;

    @Column(name = "Quantity", nullable = false)
    private Integer quantity = 0;

    @Column(name = "Sold", nullable = false)
    private Integer sold = 0;

    @Column(name = "ImageUrl")
    private String imageUrl;

    @Column(name = "IsDeleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    /** "M / Black", "M" or "Black", for messages and order lines. */
    public String label() {
        if (size != null && !size.isBlank() && color != null && !color.isBlank()) return size + " / " + color;
        return size != null && !size.isBlank() ? size : color;
    }
}
