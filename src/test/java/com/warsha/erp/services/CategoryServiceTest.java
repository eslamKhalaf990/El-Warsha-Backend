package com.warsha.erp.services;

import com.warsha.erp.dtos.CategoryDto;
import com.warsha.erp.dtos.CategoryRequest;
import com.warsha.erp.entities.Category;
import com.warsha.erp.exceptions.AppException;
import com.warsha.erp.exceptions.ConflictException;
import com.warsha.erp.exceptions.NotFoundException;
import com.warsha.erp.repository.CategoryRepository;
import com.warsha.erp.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CategoryServiceTest {

    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final CategoryService service = new CategoryService(categories, products);
    private final List<Category> rows = new ArrayList<>();

    private Category category(long id, String name) {
        Category c = new Category();
        c.setCategoryId(id);
        c.setName(name);
        rows.add(c);
        when(categories.findById(id)).thenReturn(Optional.of(c));
        return c;
    }

    private static CategoryRequest request(String name) {
        CategoryRequest r = new CategoryRequest();
        r.setName(name);
        return r;
    }

    @BeforeEach
    void setUp() {
        when(categories.findAll()).thenAnswer(inv -> List.copyOf(rows));
        when(categories.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void listsCategoriesWithTheirActiveProductCounts() {
        category(2, "Rings");
        category(1, "Bracelets");
        when(products.countActiveByCategory()).thenReturn(List.<Object[]>of(new Object[]{1L, 9L}));

        assertThat(service.getAllCategories()).containsExactly(
                new CategoryDto(1L, "Bracelets", null, null, 9),
                new CategoryDto(2L, "Rings", null, null, 0));
    }

    @Test
    void createTidiesTheNameAndBlankDescription() {
        CategoryRequest r = request("  Gift   boxes ");
        r.setDescription("   ");

        CategoryDto created = service.create(r);

        assertThat(created.name()).isEqualTo("Gift boxes");
        assertThat(created.description()).isNull();
    }

    @Test
    void duplicateNamesAreRejectedOnTheNameField() {
        category(1, "Rings");

        assertThatThrownBy(() -> service.create(request(" rings ")))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> assertThat(((AppException) e).getDetails())
                        .singleElement().satisfies(d -> assertThat(d.field()).isEqualTo("name")));
        verify(categories, never()).save(any());
    }

    @Test
    void renamingKeepsItsOwnNameButNotAnothers() {
        category(1, "Rings");
        category(2, "Chains");

        assertThat(service.update(1L, request("RINGS")).name()).isEqualTo("RINGS");
        assertThatThrownBy(() -> service.update(2L, request("rings"))).isInstanceOf(ConflictException.class);
    }

    @Test
    void updatingOrDeletingAMissingCategoryIsNotFound() {
        assertThatThrownBy(() -> service.update(99L, request("x"))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(NotFoundException.class);
        verify(categories, never()).save(any());
    }

    @Test
    void aCategoryWithProductsCannotBeDeleted() {
        category(1, "Rings");
        when(products.countActiveByCategoryId(1L)).thenReturn(3L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("3 products");
        verify(categories, never()).delete(any());
    }

    @Test
    void aCategoryLinkedOnlyToDeletedProductsIsKept() {
        category(1, "Rings");
        when(products.countAllByCategoryId(1L)).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("2 deleted products");
        verify(categories, never()).delete(any());
    }

    @Test
    void anUnusedCategoryIsDeleted() {
        Category c = category(1, "Rings");

        service.delete(1L);

        verify(categories).delete(c);
    }
}
