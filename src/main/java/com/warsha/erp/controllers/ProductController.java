package com.warsha.erp.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warsha.erp.dtos.ProductDTO;
import com.warsha.erp.dtos.ProductRequest;
import com.warsha.erp.exceptions.AppException;
import com.warsha.erp.exceptions.ErrorCode;
import com.warsha.erp.dtos.ProductImageDto;
import com.warsha.erp.dtos.ProductImageOrderRequest;
import com.warsha.erp.dtos.ProductVariantDto;
import com.warsha.erp.dtos.ProductVariantRequest;
import com.warsha.erp.entities.Product;
import com.warsha.erp.services.ProductImageService;
import com.warsha.erp.services.ProductService;
import com.warsha.erp.services.ProductVariantService;
import jakarta.validation.Valid;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductVariantService variantService;

    @Autowired
    private ProductImageService imageService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @GetMapping
    public List<ProductDTO> getAll() {
        return productService.getAllProducts();
    }

    @GetMapping("/getByCategoryId")
    public List<ProductDTO> getAllByCategoryId(@RequestParam Long categoryId) {
        return productService.getProductsByCategoryId(categoryId);
    }

    @GetMapping("/getImage")
    public ResponseEntity<Resource> getProductImage(@RequestParam String filename) {
        try {
            Resource resource = productService.getProductImage(filename);

            if (resource == null) {
                return ResponseEntity.notFound().build();
            }

            // Optional: dynamically detect MIME type
            String contentType = "application/octet-stream";
            try {
                contentType = Files.probeContentType(Paths.get(filename));
            } catch (IOException ex) {
                // fallback to octet-stream
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);

        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // All images in the image library, for the "choose an image" picker
    @GetMapping("/images")
    public List<Map<String, String>> getLibraryImages() throws IOException {
        return productService.listLibraryImages();
    }

    // Add a new image to the library without touching any product
    @PostMapping(value = "/images", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<Map<String, String>> uploadLibraryImage(@RequestPart("image") MultipartFile image) throws IOException {
        if (image == null || image.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return new ResponseEntity<>(productService.uploadLibraryImage(image), HttpStatus.CREATED);
    }

    // Assign an existing library image to a product. Body: { "filename": "abc.jpg" }
    @PutMapping("/{id}/image")
    public ProductDTO assignImage(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return productService.assignImage(id, body.get("filename"));
    }

    @GetMapping("/{id}")
    public ProductDTO getById(@PathVariable Long id) {
        return productService.getProductDtoById(id);
    }

    // --- Gallery images (the cover stays in the product's imageUrl) ---

    @GetMapping("/{id}/images")
    public List<ProductImageDto> getImages(@PathVariable Long id) {
        return imageService.list(id);
    }

    // Add an image already in the library. Body: { "filename": "abc.jpg" }
    @PostMapping("/{id}/images")
    public ResponseEntity<ProductImageDto> addImage(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return new ResponseEntity<>(imageService.addFromLibrary(id, body.get("filename")), HttpStatus.CREATED);
    }

    // Upload a new image into the library and add it to the gallery
    @PostMapping(value = "/{id}/images/upload", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<ProductImageDto> uploadImage(@PathVariable Long id,
                                                       @RequestPart("image") MultipartFile image) throws IOException {
        return new ResponseEntity<>(imageService.upload(id, image), HttpStatus.CREATED);
    }

    @PutMapping("/{id}/images/order")
    public List<ProductImageDto> reorderImages(@PathVariable Long id,
                                               @Valid @RequestBody ProductImageOrderRequest request) {
        return imageService.reorder(id, request.getImageIds());
    }

    @DeleteMapping("/{id}/images/{imageId}")
    public ResponseEntity<Void> removeImage(@PathVariable Long id, @PathVariable Long imageId) {
        imageService.remove(id, imageId);
        return ResponseEntity.noContent().build();
    }

    // --- Sizes / colors ---

    @GetMapping("/{id}/variants")
    public List<ProductVariantDto> getVariants(@PathVariable Long id) {
        return variantService.list(id);
    }

    @PostMapping("/{id}/variants")
    public ResponseEntity<ProductVariantDto> addVariant(@PathVariable Long id,
                                                        @Valid @RequestBody ProductVariantRequest request) {
        return new ResponseEntity<>(variantService.create(id, request), HttpStatus.CREATED);
    }

    @PutMapping("/{id}/variants/{variantId}")
    public ProductVariantDto updateVariant(@PathVariable Long id, @PathVariable Long variantId,
                                           @Valid @RequestBody ProductVariantRequest request) {
        return variantService.update(id, variantId, request);
    }

    @DeleteMapping("/{id}/variants/{variantId}")
    public ResponseEntity<Void> deleteVariant(@PathVariable Long id, @PathVariable Long variantId) {
        variantService.delete(id, variantId);
        return ResponseEntity.noContent().build();
    }

    /** Multipart: "product" = {@link ProductRequest} as JSON, "image" = the cover image (required). */
    @PostMapping(consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDTO create(@RequestPart("product") String productJson,
                             @RequestPart(value = "image", required = false) MultipartFile image) {
        return productService.create(readRequest(productJson), image);
    }

    /** Multipart like create; "image" is optional and replaces the cover when sent. */
    @PutMapping(value = "/{id}", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ProductDTO updateProduct(@PathVariable Long id,
                                    @RequestPart("product") String productJson,
                                    @RequestPart(value = "image", required = false) MultipartFile image) {
        return productService.update(id, readRequest(productJson), image);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.deleteProduct(id);
    }

    private ProductRequest readRequest(String json) {
        try {
            return objectMapper.readValue(json, ProductRequest.class);
        } catch (JsonProcessingException e) {
            throw new AppException(ErrorCode.MALFORMED_REQUEST,
                    "The product details could not be read. Please check the numbers and try again.");
        }
    }
}
