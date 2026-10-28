package com.warsha.erp.services;

import com.warsha.erp.dtos.CategoryDto;
import com.warsha.erp.dtos.CategoryRequest;
import com.warsha.erp.entities.Category;
import com.warsha.erp.exceptions.ConflictException;
import com.warsha.erp.exceptions.NotFoundException;
import com.warsha.erp.repository.CategoryRepository;
import com.warsha.erp.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    /** Every category in creation order, with its number of products that are not deleted. */
    @Transactional(readOnly = true)
    public List<CategoryDto> getAllCategories() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : productRepository.countActiveByCategory()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return categoryRepository.findAll().stream()
                .sorted(Comparator.comparing(Category::getCategoryId))
                .map(c -> CategoryDto.of(c, counts.getOrDefault(c.getCategoryId(), 0L)))
                .toList();
    }

    @Transactional
    public CategoryDto create(CategoryRequest request) {
        Category category = new Category();
        apply(category, request, null);
        category.setCreatedAt(LocalDateTime.now());
        return CategoryDto.of(categoryRepository.save(category), 0);
    }

    @Transactional
    public CategoryDto update(Long id, CategoryRequest request) {
        Category category = find(id);
        apply(category, request, id);
        category.setUpdatedAt(LocalDateTime.now());
        return CategoryDto.of(categoryRepository.save(category), productRepository.countActiveByCategoryId(id));
    }

    /**
     * Products keep a link to their category even after they are deleted, so a
     * category can only be removed once no product row points at it.
     */
    @Transactional
    public void delete(Long id) {
        Category category = find(id);
        long active = productRepository.countActiveByCategoryId(id);
        if (active > 0) {
            throw new ConflictException("\"" + category.getName() + "\" still has " + active
                    + (active == 1 ? " product" : " products") + ". Move them to another category first.");
        }
        long deleted = productRepository.countAllByCategoryId(id);
        if (deleted > 0) {
            throw new ConflictException("\"" + category.getName() + "\" is linked to " + deleted
                    + (deleted == 1 ? " deleted product" : " deleted products")
                    + " kept for order history, so it can't be removed.");
        }
        categoryRepository.delete(category);
    }

    private Category find(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new NotFoundException("Category", id));
    }

    private void apply(Category category, CategoryRequest request, Long ownId) {
        String name = clean(request.getName());
        boolean taken = categoryRepository.findAll().stream()
                .anyMatch(c -> !c.getCategoryId().equals(ownId) && key(c.getName()).equals(key(name)));
        if (taken) {
            throw new ConflictException("name", "A category named \"" + name + "\" already exists.");
        }
        category.setName(name);
        category.setDescription(blankToNull(request.getDescription()));
        category.setImageUrl(blankToNull(request.getImageUrl()));
    }

    /** Trims and collapses inner spaces: "  Gift   boxes " → "Gift boxes". */
    static String clean(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    /** Comparison key for duplicate names: cleaned and case-insensitive. */
    static String key(String name) {
        return clean(name).toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        String c = clean(s);
        return c.isEmpty() ? null : c;
    }
}
