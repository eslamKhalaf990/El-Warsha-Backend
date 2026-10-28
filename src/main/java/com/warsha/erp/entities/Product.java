package com.warsha.erp.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Setter
@Getter
@Entity
@Table(name = "Products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ProductID")
    private Long ProductID;

    @Column(name = "Name")
    private String Name;

    @Column(name = "NameAr")
    private String NameAr;

    @Column(name = "Description")
    private String Description;

    @Column(name = "DescriptionAr")
    private String DescriptionAr;

    @Column(name = "BuyingPrice")
    private Double BuyingPrice;

    @Column(name = "SellingPrice")
    private Double SellingPrice;

    @Column(name = "Discount")
    private Double Discount = 0.0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Category", nullable = false)
    private Category Category;

    @Column(name = "ImageUrl")
    private String ImageUrl;

    @Column(name = "SKU", insertable = false, updatable = false)
    private String Sku;

    @Column(name = "Quantity")
    private String Quantity;

    @Column(name = "Sold")
    private Integer Sold = 0;

    @Column(name = "IsDeleted")
    private String deleted;

    @Column(name = "CreatedAt")
    private LocalDateTime CreatedAt;

    @Column(name = "DeletedAt")
    private LocalDateTime DeletedAt;

    @Column(name = "UpdatedAt")
    private LocalDateTime UpdatedAt;
}
