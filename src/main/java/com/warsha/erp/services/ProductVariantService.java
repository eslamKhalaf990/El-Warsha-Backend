package com.warsha.erp.services;

import com.warsha.erp.dtos.ProductVariantDto;
import com.warsha.erp.dtos.ProductVariantRequest;
import com.warsha.erp.entities.Product;
import com.warsha.erp.entities.ProductVariant;
import com.warsha.erp.exceptions.ConflictException;
import com.warsha.erp.exceptions.NotFoundException;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.repository.ProductRepository;
import com.warsha.erp.repository.ProductVariantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Sizes/colors of a product. Every change locks the product row (like orders do) and
 * resets the product's quantity to the total of its variants.
 */
@Service
public class ProductVariantService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;

    public ProductVariantService(ProductRepository productRepository, ProductVariantRepository variantRepository) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductVariantDto> list(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product", productId));
        return variantRepository.findActiveByProductId(productId).stream()
                .map(v -> ProductVariantDto.of(v, product))
                .toList();
    }

    @Transactional
    public ProductVariantDto create(Long productId, ProductVariantRequest request) {
        Product product = lockProduct(productId);
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setCreatedAt(LocalDateTime.now());
        apply(variant, request, product);
        variantRepository.save(variant);
        syncTotal(product);
        return ProductVariantDto.of(variant, product);
    }

    @Transactional
    public ProductVariantDto update(Long productId, Long variantId, ProductVariantRequest request) {
        Product product = lockProduct(productId);
        ProductVariant variant = findVariant(product, variantId);
        apply(variant, request, product);
        variant.setUpdatedAt(LocalDateTime.now());
        variantRepository.save(variant);
        syncTotal(product);
        return ProductVariantDto.of(variant, product);
    }

    /** Soft delete: past orders still point at the variant. */
    @Transactional
    public void delete(Long productId, Long variantId) {
        Product product = lockProduct(productId);
        ProductVariant variant = findVariant(product, variantId);
        variant.setDeleted(true);
        variant.setUpdatedAt(LocalDateTime.now());
        variantRepository.save(variant);
        syncTotal(product);
    }

    // ---------------------------------------------------------------- helpers

    private Product lockProduct(Long productId) {
        return productRepository.findAllByIdWithLock(List.of(productId)).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("Product", productId));
    }

    private ProductVariant findVariant(Product product, Long variantId) {
        return variantRepository.findById(variantId)
                .filter(v -> !v.isDeleted() && v.getProduct().getProductID().equals(product.getProductID()))
                .orElseThrow(() -> new NotFoundException("Variant", variantId));
    }

    private void apply(ProductVariant variant, ProductVariantRequest request, Product product) {
        String size = blankToNull(request.getSize());
        String color = blankToNull(request.getColor());
        if (size == null && color == null) {
            throw new ValidationException("size", "Enter a size, a color, or both.");
        }
        boolean duplicate = variantRepository.findActiveByProductId(product.getProductID()).stream()
                .filter(v -> !Objects.equals(v.getId(), variant.getId()))
                .anyMatch(v -> sameText(v.getSize(), size) && sameText(v.getColor(), color));
        if (duplicate) {
            throw new ConflictException("'" + product.getName() + "' already has this size/color.");
        }
        variant.setSize(size);
        variant.setColor(color);
        variant.setColorAr(blankToNull(request.getColorAr()));
        variant.setColorHex(blankToNull(request.getColorHex()));
        variant.setSku(blankToNull(request.getSku()));
        variant.setSellingPrice(request.getSellingPrice());
        variant.setQuantity(request.getQuantity());
        variant.setImageUrl(blankToNull(request.getImageUrl()));
    }

    /** The product's quantity is the total of its active variants. */
    private void syncTotal(Product product) {
        int total = variantRepository.findActiveByProductId(product.getProductID()).stream()
                .mapToInt(v -> v.getQuantity() != null ? v.getQuantity() : 0)
                .sum();
        product.setQuantity(String.valueOf(total));
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);
    }

    private static boolean sameText(String a, String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
