package com.warsha.erp.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Body of POST /category and PUT /category/{id}. */
@Getter
@Setter
public class CategoryRequest {

    @NotBlank(message = "Enter the category name.")
    @Size(max = 100, message = "Category name can be at most 100 characters.")
    private String name;

    @Size(max = 255, message = "Description can be at most 255 characters.")
    private String description;

    @Size(max = 255, message = "Image link can be at most 255 characters.")
    private String imageUrl;
}
