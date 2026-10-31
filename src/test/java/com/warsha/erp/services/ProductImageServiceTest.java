package com.warsha.erp.services;

import com.warsha.erp.dtos.ProductImageDto;
import com.warsha.erp.entities.Product;
import com.warsha.erp.entities.ProductImage;
import com.warsha.erp.exceptions.BusinessRuleException;
import com.warsha.erp.exceptions.NotFoundException;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.repository.ProductImageRepository;
import com.warsha.erp.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductImageServiceTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductImageRepository images = mock(ProductImageRepository.class);
    private final ProductService productService = mock(ProductService.class);
    private final ProductImageService service = new ProductImageService(products, images, productService);

    private final Product product = StockServiceTest.product(1, "5", 0);
    private final List<ProductImage> gallery = new ArrayList<>();

    @BeforeEach
    void setUp() {
        product.setImageUrl("/images/cover.jpg");
        when(products.findById(1L)).thenReturn(Optional.of(product));
        when(images.findByProductId(1L)).thenAnswer(inv -> List.copyOf(gallery));
        when(images.save(any())).thenAnswer(inv -> {
            ProductImage i = inv.getArgument(0);
            i.setId(100L + gallery.size());
            gallery.add(i);
            return i;
        });
    }

    private ProductImage image(long id, String url, int order) {
        ProductImage i = new ProductImage();
        i.setId(id);
        i.setProduct(product);
        i.setImageUrl(url);
        i.setSortOrder(order);
        gallery.add(i);
        return i;
    }

    @Test
    void libraryImagesAreAddedAtTheEnd() {
        image(1, "/images/a.jpg", 0);
        when(productService.libraryImageUrl("b.jpg")).thenReturn("/images/b.jpg");

        ProductImageDto added = service.addFromLibrary(1L, "b.jpg");

        assertThat(added.imageUrl()).isEqualTo("/images/b.jpg");
        assertThat(added.sortOrder()).isEqualTo(1);
    }

    @Test
    void theCoverOrADuplicateIsRejected() {
        image(1, "/images/a.jpg", 0);
        when(productService.libraryImageUrl("a.jpg")).thenReturn("/images/a.jpg");
        when(productService.libraryImageUrl("cover.jpg")).thenReturn("/images/cover.jpg");

        assertThatThrownBy(() -> service.addFromLibrary(1L, "a.jpg")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.addFromLibrary(1L, "cover.jpg")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void badFilenamesAreAValidationError() {
        when(productService.libraryImageUrl("../x")).thenThrow(new IllegalArgumentException("Invalid filename"));
        assertThatThrownBy(() -> service.addFromLibrary(1L, "../x")).isInstanceOf(ValidationException.class);
    }

    @Test
    void theGalleryIsCapped() {
        IntStream.range(0, ProductImageService.MAX_IMAGES).forEach(i -> image(i, "/images/" + i + ".jpg", i));
        when(productService.libraryImageUrl("new.jpg")).thenReturn("/images/new.jpg");
        assertThatThrownBy(() -> service.addFromLibrary(1L, "new.jpg"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A product can have up to 10 extra images.");
    }

    @Test
    void reorderFollowsTheGivenIds() {
        ProductImage a = image(1, "/images/a.jpg", 0);
        ProductImage b = image(2, "/images/b.jpg", 1);

        List<ProductImageDto> result = service.reorder(1L, List.of(2L, 1L));

        assertThat(result).extracting(ProductImageDto::id).containsExactly(2L, 1L);
        assertThat(b.getSortOrder()).isZero();
        assertThat(a.getSortOrder()).isEqualTo(1);
    }

    @Test
    void reorderNeedsEveryImageOnce() {
        image(1, "/images/a.jpg", 0);
        image(2, "/images/b.jpg", 1);
        assertThatThrownBy(() -> service.reorder(1L, List.of(1L))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.reorder(1L, List.of(1L, 1L))).isInstanceOf(ValidationException.class);
    }

    @Test
    void imagesOfAnotherProductCannotBeRemoved() {
        ProductImage other = new ProductImage();
        other.setId(9L);
        other.setProduct(StockServiceTest.product(2, "1", 0));
        when(images.findById(9L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.remove(1L, 9L)).isInstanceOf(NotFoundException.class);
        verify(images, never()).delete(any());
    }
}
