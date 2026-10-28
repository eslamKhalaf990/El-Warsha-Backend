package com.warsha.erp.services;

import com.warsha.erp.dtos.ProductDTO;
import com.warsha.erp.dtos.ProductImageDto;
import com.warsha.erp.dtos.ProductVariantDto;
import com.warsha.erp.entities.Product;
import com.warsha.erp.entities.ProductImage;
import com.warsha.erp.entities.ProductVariant;
import com.warsha.erp.exceptions.NotFoundException;
import com.warsha.erp.repository.ProductRepository;
import com.warsha.erp.repository.ProductImageRepository;
import com.warsha.erp.repository.ProductVariantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;

    // This pulls the path from your application.properties,
    // defaulting to your Ubuntu path if not specified
    @Value("${image.upload.dir:/root/warsha_images/}")
    private String uploadDir;

    @Autowired
    public ProductService(ProductRepository productRepository, ProductVariantRepository variantRepository,
                          ProductImageRepository imageRepository) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.imageRepository = imageRepository;
    }

    public List<ProductDTO> getAllProducts() {
        return toDtos(productRepository.findAllNotDeleted());
    }

    public List<ProductDTO> getProductsByCategoryId(Long categoryId) {
        return toDtos(productRepository.findByCategoryId(categoryId));
    }

    /** One product with its sizes/colors, for the product page. */
    public ProductDTO getProductDtoById(Long id) {
        return toDtos(List.of(getProductById(id))).get(0);
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product", id));
    }

    // You likely won't need this method anymore since Nginx serves the images,
    // but I've updated it to read from the local hard drive just in case.
    public Resource getProductImage(String filename) throws MalformedURLException {
        Path filePath = Paths.get(uploadDir).resolve(filename).normalize();
        Resource resource = new UrlResource(filePath.toUri());

        if (resource.exists() && resource.isReadable()) {
            return resource;
        } else {
            return null;
        }
    }

    public Product createProduct(Product product, MultipartFile image) {
        product.setCreatedAt(LocalDateTime.now());

        if (image != null && !image.isEmpty()) {
            try {
                String imageUrl = saveImageToDisk(image);
                product.setImageUrl(imageUrl);
            } catch (IOException e) {
                throw new RuntimeException("Failed to save image to disk", e);
            }
        }

        return productRepository.save(product);
    }

    public void updateProduct(Long id, Product updatedProduct) {
        Product existing = getProductById(id);
        existing.setName(updatedProduct.getName());
        existing.setNameAr(updatedProduct.getNameAr());
        existing.setDescription(updatedProduct.getDescription());
        existing.setDescriptionAr(updatedProduct.getDescriptionAr());
        existing.setBuyingPrice(updatedProduct.getBuyingPrice());
        existing.setSellingPrice(updatedProduct.getSellingPrice());
        existing.setDiscount(updatedProduct.getDiscount());
        // With sizes/colors the stock lives on the variants and the total is kept by the server
        if (variantRepository.findActiveByProductId(id).isEmpty()) {
            existing.setQuantity(updatedProduct.getQuantity());
        }
        existing.setCategory(updatedProduct.getCategory());
        existing.setUpdatedAt(LocalDateTime.now());
        productRepository.save(existing);
    }

    public Product updateProductWithImage(Long id, Product updatedProduct, MultipartFile image) {
        Product existing = getProductById(id);

        existing.setName(updatedProduct.getName());
        existing.setNameAr(updatedProduct.getNameAr());
        existing.setDescription(updatedProduct.getDescription());
        existing.setDescriptionAr(updatedProduct.getDescriptionAr());
        existing.setBuyingPrice(updatedProduct.getBuyingPrice());
        existing.setSellingPrice(updatedProduct.getSellingPrice());
        existing.setDiscount(updatedProduct.getDiscount());
        // With sizes/colors the stock lives on the variants and the total is kept by the server
        if (variantRepository.findActiveByProductId(id).isEmpty()) {
            existing.setQuantity(updatedProduct.getQuantity());
        }
        existing.setCategory(updatedProduct.getCategory());
        existing.setUpdatedAt(LocalDateTime.now());

        if (image != null && !image.isEmpty()) {
            try {
                // Old image stays in the library: other products may still use it
                // Save the new image
                String imageUrl = saveImageToDisk(image);
                existing.setImageUrl(imageUrl);
            } catch (IOException e) {
                throw new RuntimeException("Failed to save image to disk", e);
            }
        }

        return productRepository.save(existing);
    }

    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id " + id));

        // Image file is kept: it belongs to the shared image library
        // Soft delete: mark as deleted
        product.setDeleted("true");
        product.setUpdatedAt(LocalDateTime.now());
        product.setDeletedAt(LocalDateTime.now());
        productRepository.save(product);
    }

    public void deleteAll() {
        productRepository.deleteAll();
    }

    // --- Image Library (all images in uploadDir) ---

    public List<Map<String, String>> listLibraryImages() throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            return List.of();
        }

        try (Stream<Path> files = Files.list(uploadPath)) {
            return files
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> !name.startsWith("."))
                    .sorted()
                    .map(name -> Map.of("filename", name, "url", "/images/" + name))
                    .collect(Collectors.toList());
        }
    }

    public Map<String, String> uploadLibraryImage(MultipartFile image) throws IOException {
        String url = saveImageToDisk(image);
        return Map.of("filename", url.replace("/images/", ""), "url", url);
    }

    public Product assignImage(Long productId, String filename) {
        String url = libraryImageUrl(filename);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id " + productId));
        product.setImageUrl(url);
        product.setUpdatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    /**
     * Checks that {@code filename} is a plain file name of an image in the library
     * and returns its URL ({@code /images/<name>}).
     */
    public String libraryImageUrl(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename is required");
        }

        // Only plain file names inside the image folder are allowed
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path filePath = uploadPath.resolve(filename).normalize();
        if (filePath.getParent() == null || !filePath.getParent().equals(uploadPath)) {
            throw new IllegalArgumentException("Invalid filename");
        }
        if (!Files.isRegularFile(filePath)) {
            throw new EntityNotFoundException("Image not found: " + filename);
        }
        return "/images/" + filePath.getFileName();
    }

    // --- Private Helper Methods ---

    /** Maps products to DTOs, loading all their variants and gallery images in one query each. */
    private List<ProductDTO> toDtos(List<Product> products) {
        List<Long> ids = products.stream().map(Product::getProductID).toList();
        Map<Long, List<ProductVariant>> variantsByProduct = ids.isEmpty() ? Map.of()
                : variantRepository.findActiveByProductIds(ids).stream()
                        .collect(Collectors.groupingBy(v -> v.getProduct().getProductID()));
        Map<Long, List<ProductImage>> imagesByProduct = ids.isEmpty() ? Map.of()
                : imageRepository.findByProductIds(ids).stream()
                        .collect(Collectors.groupingBy(i -> i.getProduct().getProductID()));

        return products.stream()
                .map(product -> {
                    ProductDTO dto = new ProductDTO();
                    dto.setId(product.getProductID());
                    dto.setName(product.getName());
                    dto.setNameAr(product.getNameAr());
                    dto.setDeleted(product.getDeleted());
                    dto.setDeletedAt(product.getDeletedAt());
                    dto.setDescription(product.getDescription());
                    dto.setDescriptionAr(product.getDescriptionAr());
                    dto.setBuyingPrice(product.getBuyingPrice());
                    dto.setSellingPrice(product.getSellingPrice());
                    double discount = product.getDiscount() != null ? product.getDiscount() : 0;
                    dto.setDiscount(discount);
                    dto.setTotalPrice(product.getSellingPrice() != null ? product.getSellingPrice() - discount : null);
                    dto.setImageUrl(product.getImageUrl());
                    dto.setSku(product.getSku());
                    dto.setQuantity(product.getQuantity());
                    if (product.getCategory() != null) {
                        dto.setCategoryId(product.getCategory().getCategoryId());
                        dto.setCategoryName(product.getCategory().getName());
                    }
                    List<ProductVariant> variants = variantsByProduct.getOrDefault(product.getProductID(), List.of());
                    dto.setHasVariants(!variants.isEmpty());
                    dto.setVariants(variants.stream().map(v -> ProductVariantDto.of(v, product)).toList());
                    dto.setImages(imagesByProduct.getOrDefault(product.getProductID(), List.of()).stream()
                            .map(ProductImageDto::of).toList());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private String saveImageToDisk(MultipartFile image) throws IOException {
        Path uploadPath = Paths.get(uploadDir);

        // Create the directory if it doesn't exist yet
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Keep original extension, but give it a UUID name to prevent overwriting
        String originalFilename = image.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String uniqueFileName = UUID.randomUUID().toString() + extension;
        Path filePath = uploadPath.resolve(uniqueFileName);

        // Copy the file to the target location
        Files.copy(image.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Return the exact string that needs to be saved in the database
        return "/images/" + uniqueFileName;
    }
}
