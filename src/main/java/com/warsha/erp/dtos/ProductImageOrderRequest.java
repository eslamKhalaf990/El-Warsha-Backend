package com.warsha.erp.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Body of PUT /products/{id}/images/order: every gallery image id, in the new order. */
@Getter
@Setter
public class ProductImageOrderRequest {

    @NotNull(message = "Send the image ids in their new order.")
    private List<Long> imageIds;
}
