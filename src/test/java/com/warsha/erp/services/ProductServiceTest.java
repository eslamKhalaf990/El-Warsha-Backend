package com.warsha.erp.services;

import com.warsha.erp.dtos.ProductRequest;
import com.warsha.erp.entities.Category;
import com.warsha.erp.entities.Product;
import com.warsha.erp.entities.ProductVariant;
import com.warsha.erp.exceptions.AppException;
import com.warsha.erp.exceptions.NotFoundException;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.repository.CategoryRepository;
import com.warsha.erp.repository.ProductImageRepository;
import com.warsha.erp.repository.ProductRepository;
import com.warsha.erp.repository.ProductVariantRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class ProductServiceTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductVariantRepository variants = mock(ProductVariantRepository.class);
    private final ProductImageRepository images = mock(ProductImageRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final ProductService service = new ProductService(products, variants, images, categories,
            Validation.buildDefaultValidatorFactory().getValidator());
    private Product saved;

    @BeforeEach
    void setUp() throws Exception {
        Path dir = Files.createTempDirectory("warsha-images");
        ReflectionTestUtils.setField(service, "uploadDir", dir.toString() + "/");
        Category rings = new Category();
        rings.setCategoryId(3L);
        rings.setName("Rings");
        when(categories.findById(3L)).thenReturn(Optional.of(rings));
        when(products.save(any())).thenAnswer(inv -> {
            saved = inv.getArgument(0);
            if (saved.getProductID() == null) saved.setProductID(50L);
            when(products.findById(saved.getProductID())).thenReturn(Optional.of(saved));
            return saved;
        });
        when(variants.findActiveByProductIds(anyList())).thenReturn(List.of());
        when(images.findByProductIds(anyList())).thenReturn(List.of());
    }

    private static ProductRequest request() {
        ProductRequest r = new ProductRequest();
        r.setName("  Silver ring ");
        r.setNameAr("  ");
        r.setBuyingPrice(100.0);
        r.setSellingPrice(250.0);
        r.setQuantity(7);
        r.setCategoryId(3L);
        return r;
    }

    private static MockMultipartFile image() {
        return new MockMultipartFile("image", "ring.png", "image/png", new byte[]{1, 2, 3});
    }

    @Test
    void createSavesATidyProductWithItsImage() {
        var dto = service.create(request(), image());

        assertThat(saved.getName()).isEqualTo("Silver ring");
        assertThat(saved.getNameAr()).isNull();
        assertThat(saved.getDiscount()).isZero();
        assertThat(saved.getQuantity()).isEqualTo("7");
        assertThat(saved.getImageUrl()).startsWith("/images/");
        assertThat(dto.getCategoryName()).isEqualTo("Rings");
    }

    @Test
    void everyInvalidFieldIsReportedAtOnce() {
        ProductRequest r = new ProductRequest();
        r.setSellingPrice(-1.0);

        assertThatThrownBy(() -> service.create(r, image()))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getDetails())
                        .extracting(d -> d.field())
                        .containsExactly("buyingPrice", "categoryId", "name", "sellingPrice"));
        verify(products, never()).save(any());
    }

    @Test
    void aNewProductNeedsAnImage() {
        assertThatThrownBy(() -> service.create(request(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Choose a product image.");
    }

    @Test
    void discountCannotExceedThePrice() {
        ProductRequest r = request();
        r.setDiscount(300.0);
        assertThatThrownBy(() -> service.create(r, image()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Discount can't be more than the selling price.");
    }

    @Test
    void anUnknownCategoryIsAFieldError() {
        ProductRequest r = request();
        r.setCategoryId(99L);
        assertThatThrownBy(() -> service.create(r, image()))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((AppException) e).getDetails().get(0).field()).isEqualTo("categoryId"));
    }

    @Test
    void updateKeepsVariantStockAndTheOldImage() {
        Product existing = new Product();
        existing.setProductID(8L);
        existing.setQuantity("12");
        existing.setImageUrl("/images/old.png");
        when(products.findById(8L)).thenReturn(Optional.of(existing));
        when(variants.findActiveByProductId(8L)).thenReturn(List.of(new ProductVariant()));

        service.update(8L, request(), null);

        assertThat(existing.getQuantity()).isEqualTo("12");
        assertThat(existing.getImageUrl()).isEqualTo("/images/old.png");
        assertThat(existing.getName()).isEqualTo("Silver ring");
    }

    @Test
    void deleteIsSoftAndMissingProductsAreNotFound() {
        Product existing = new Product();
        existing.setProductID(8L);
        when(products.findById(8L)).thenReturn(Optional.of(existing));

        service.deleteProduct(8L);

        assertThat(existing.getDeleted()).isEqualTo("true");
        verify(products, never()).delete(any());
        assertThatThrownBy(() -> service.deleteProduct(9L)).isInstanceOf(NotFoundException.class);
    }
}
