package com.warsha.erp.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** An extra gallery image of a product; the cover stays in {@link Product#getImageUrl()}. */
@Setter
@Getter
@Entity
@Table(name = "ProductImages")
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ImageID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ProductID", nullable = false)
    private Product product;

    @Column(name = "ImageUrl", nullable = false)
    private String imageUrl;

    @Column(name = "SortOrder", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;
}
