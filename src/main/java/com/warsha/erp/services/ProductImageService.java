package com.warsha.erp.services;

import com.warsha.erp.dtos.ProductImageDto;
import com.warsha.erp.entities.Product;
import com.warsha.erp.entities.ProductImage;
import com.warsha.erp.exceptions.BusinessRuleException;
import com.warsha.erp.exceptions.NotFoundException;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.repository.ProductImageRepository;
import com.warsha.erp.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

/**
 * Extra gallery images of a product. The cover image stays on the product itself;
 * files live in the shared image library, so removing a gallery image never deletes the file.
 */
@Service
public class ProductImageService {

    /** Gallery images per product, not counting the cover. */
    static final int MAX_IMAGES = 10;

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final ProductService productService;

    public ProductImageService(ProductRepository productRepository, ProductImageRepository imageRepository,
                               ProductService productService) {
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.productService = productService;
    }

    @Transactional(readOnly = true)
    public List<ProductImageDto> list(Long productId) {
        findProduct(productId);
        return imageRepository.findByProductId(productId).stream().map(ProductImageDto::of).toList();
    }

    /** Adds an image that is already in the library. */
    @Transactional
    public ProductImageDto addFromLibrary(Long productId, String filename) {
        Product product = findProduct(productId);
        String url;
        try {
            url = productService.libraryImageUrl(filename);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("filename", "Choose an image from the library.");
        }
        return add(product, url);
    }

    /** Uploads a new image to the library and adds it to the gallery. */
    @Transactional
    public ProductImageDto upload(Long productId, MultipartFile file) throws IOException {
        Product product = findProduct(productId);
        if (file == null || file.isEmpty()) {
            throw new ValidationException("image", "Choose an image to upload.");
        }
        checkRoom(product);
        return add(product, productService.uploadLibraryImage(file).get("url"));
    }

    @Transactional
    public void remove(Long productId, Long imageId) {
        imageRepository.delete(findImage(productId, imageId));
    }

    /** Puts the gallery in the given order. {@code imageIds} must list every image exactly once. */
    @Transactional
    public List<ProductImageDto> reorder(Long productId, List<Long> imageIds) {
        findProduct(productId);
        List<ProductImage> images = imageRepository.findByProductId(productId);
        boolean sameSet = imageIds.size() == images.size()
                && new HashSet<>(imageIds).equals(new HashSet<>(images.stream().map(ProductImage::getId).toList()));
        if (!sameSet) {
            throw new ValidationException("imageIds", "Send every image of the product exactly once.");
        }
        for (ProductImage image : images) {
            image.setSortOrder(imageIds.indexOf(image.getId()));
        }
        imageRepository.saveAll(images);
        return images.stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .map(ProductImageDto::of)
                .toList();
    }

    // ---------------------------------------------------------------- helpers

    private ProductImageDto add(Product product, String url) {
        List<ProductImage> existing = checkRoom(product);
        if (url.equals(product.getImageUrl()) || existing.stream().anyMatch(i -> url.equals(i.getImageUrl()))) {
            throw new BusinessRuleException("This image is already in the product's gallery.");
        }
        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setImageUrl(url);
        image.setSortOrder(existing.stream().mapToInt(i -> i.getSortOrder() != null ? i.getSortOrder() : 0)
                .max().orElse(-1) + 1);
        image.setCreatedAt(LocalDateTime.now());
        return ProductImageDto.of(imageRepository.save(image));
    }

    private List<ProductImage> checkRoom(Product product) {
        List<ProductImage> existing = imageRepository.findByProductId(product.getProductID());
        if (existing.size() >= MAX_IMAGES) {
            throw new BusinessRuleException("A product can have up to " + MAX_IMAGES + " extra images.");
        }
        return existing;
    }

    private Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product", productId));
    }

    private ProductImage findImage(Long productId, Long imageId) {
        return imageRepository.findById(imageId)
                .filter(i -> i.getProduct().getProductID().equals(productId))
                .orElseThrow(() -> new NotFoundException("Image", imageId));
    }
}
