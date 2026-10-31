package com.warsha.erp.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@Entity
@Table(name = "Categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CategoryID")
    private Long CategoryId;

    @Column(name = "Name")
    private String Name;

    @Column(name = "Description")
    private String Description;

    @Column(name = "ImageUrl")
    private String ImageUrl;

    @Column(name = "CreatedAt")
    private LocalDateTime CreatedAt;

    @Column(name = "UpdatedAt")
    private LocalDateTime UpdatedAt;
}
