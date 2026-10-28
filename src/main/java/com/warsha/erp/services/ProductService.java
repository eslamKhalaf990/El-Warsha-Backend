package com.warsha.erp.services;

import com.warsha.erp.dtos.ProductRequest;
import com.warsha.erp.entities.Category;
import com.warsha.erp.exceptions.ApiError;
import com.warsha.erp.exceptions.AppException;
import com.warsha.erp.exceptions.ErrorCode;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.repository.CategoryRepository;
import jakarta.validation.Validator;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
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
    private final CategoryRepository categoryRepository;
    private final Validator validator;

    // This pulls the path from your application.properties,
    // defaulting to your Ubuntu path if not specified
    @Value("${image.upload.dir:/root/warsha_images/}")
    private String uploadDir;

    @Autowired
    public ProductService(ProductRepository productRepository, ProductVariantRepository variantRepository,
                          ProductImageRepository imageRepository, CategoryRepository categoryRepository,
                          Validator validator) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.imageRepository = imageRepository;
        this.categoryRepository = categoryRepository;
        this.validator = validator;
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

    /** Creates a product from the ERP form. A cover image is required. */
    @Transactional
    public ProductDTO create(ProductRequest request, MultipartFile image) {
        validate(request);
        if (image == null || image.isEmpty()) {
            throw new ValidationException("image", "Choose a product image.");
        }
        Product product = new Product();
        apply(product, request, true);
        product.setSold(0);
        product.setCreatedAt(LocalDateTime.now());
        product.setImageUrl(storeImage(image));
        return getProductDtoById(productRepository.save(product).getProductID());
    }

    /** Updates a product; the image is replaced only when a new one is sent. */
    @Transactional
    public ProductDTO update(Long id, ProductRequest request, MultipartFile image) {
        validate(request);
        Product product = getProductById(id);
        // With sizes/colors the stock lives on the variants and the total is kept by the server
        apply(product, request, variantRepository.findActiveByProductId(id).isEmpty());
        product.setUpdatedAt(LocalDateTime.now());
        if (image != null && !image.isEmpty()) {
            // Old image stays in the library: other products may still use it
            product.setImageUrl(storeImage(image));
        }
        productRepository.save(product);
        return getProductDtoById(id);
    }

    /** Soft delete: the product disappears from the store and the ERP, order history keeps it. */
    @Transactional
    public void deleteProduct(Long id) {
        Product product = getProductById(id);
        product.setDeleted("true");
        product.setUpdatedAt(LocalDateTime.now());
        product.setDeletedAt(LocalDateTime.now());
        productRepository.save(product);
    }

    private void validate(ProductRequest request) {
        List<ApiError.FieldViolation> details = validator.validate(request).stream()
                .map(v -> new ApiError.FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .sorted(Comparator.comparing(ApiError.FieldViolation::field))
                .toList();
        if (!details.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    details.size() == 1 ? details.get(0).message() : ErrorCode.VALIDATION_FAILED.defaultMessage(),
                    details);
        }
        double discount = request.getDiscount() == null ? 0 : request.getDiscount();
        if (discount > request.getSellingPrice()) {
            throw new ValidationException("discount", "Discount can't be more than the selling price.");
        }
    }

    private void apply(Product product, ProductRequest request, boolean setStock) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ValidationException("categoryId", "Choose a category from the list."));
        product.setName(request.getName().trim());
        product.setNameAr(blankToNull(request.getNameAr()));
        product.setDescription(blankToNull(request.getDescription()));
        product.setDescriptionAr(blankToNull(request.getDescriptionAr()));
        product.setBuyingPrice(request.getBuyingPrice());
        product.setSellingPrice(request.getSellingPrice());
        product.setDiscount(request.getDiscount() == null ? 0.0 : request.getDiscount());
        product.setCategory(category);
        if (setStock) {
            product.setQuantity(String.valueOf(request.getQuantity() == null ? 0 : request.getQuantity()));
        }
    }

    private String storeImage(MultipartFile image) {
        try {
            return saveImageToDisk(image);
        } catch (IOException e) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "The image couldn't be saved. Please try again.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
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

    public ProductDTO assignImage(Long productId, String filename) {
        Product product = getProductById(productId);
        String url;
        try {
            url = libraryImageUrl(filename);
        } catch (IllegalArgumentException | EntityNotFoundException e) {
            throw new ValidationException("filename", "Choose an image from the library.");
        }
        product.setImageUrl(url);
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);
        return getProductDtoById(productId);
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
