package com.warsha.erp.entities;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "OrderItems")
public class OrderItems {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name= "OrderItemID")
    private Long id;

    // Quantity of this product in the order
    @Column(name= "Quantity", nullable = false)
    private Integer quantity;

    // Quantity of this product in the order
    @Column(name= "UnitPrice")
    private Double unitPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "OrderID", nullable = false)
    @JsonBackReference
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ProductID", nullable = false)
    private Product product;

    // Size/color that was ordered; null for products without variants
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "VariantID")
    private ProductVariant variant;

    /** "Linen Shirt (XL / Black)", or just the product name without a variant. */
    public String displayName() {
        String name = product.getName();
        return variant == null ? name : name + " (" + variant.label() + ")";
    }
}
