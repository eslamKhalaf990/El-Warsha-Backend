package com.warsha.erp.dtos;

import com.warsha.erp.entities.Product;
import com.warsha.erp.entities.ProductVariant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductVariantDto {
    private Long id;
    private Long productId;
    private String size;
    private String color;
    private String colorAr;
    private String colorHex;
    private String sku;
    /** The variant's own price, or null when it uses the product's price. */
    private Double sellingPrice;
    /** Price before discount: the variant's price, else the product's. */
    private Double price;
    /** Price after the product discount. */
    private Double totalPrice;
    private int quantity;
    private boolean inStock;
    private String imageUrl;

    public static ProductVariantDto of(ProductVariant v, Product product) {
        ProductVariantDto dto = new ProductVariantDto();
        dto.setId(v.getId());
        dto.setProductId(product.getProductID());
        dto.setSize(v.getSize());
        dto.setColor(v.getColor());
        dto.setColorAr(v.getColorAr());
        dto.setColorHex(v.getColorHex());
        dto.setSku(v.getSku());
        dto.setSellingPrice(v.getSellingPrice());
        double price = v.getSellingPrice() != null ? v.getSellingPrice()
                : product.getSellingPrice() != null ? product.getSellingPrice() : 0;
        double discount = product.getDiscount() != null ? product.getDiscount() : 0;
        dto.setPrice(price);
        dto.setTotalPrice(price - discount);
        int qty = v.getQuantity() != null ? v.getQuantity() : 0;
        dto.setQuantity(qty);
        dto.setInStock(qty > 0);
        dto.setImageUrl(v.getImageUrl());
        return dto;
    }
}
