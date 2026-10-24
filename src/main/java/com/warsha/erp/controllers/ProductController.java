package com.warsha.erp.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warsha.erp.dtos.ProductDTO;
import com.warsha.erp.entities.Product;
import com.warsha.erp.services.ProductService;
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
    public ResponseEntity<?> assignImage(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(productService.assignImage(id, body.get("filename")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @PostMapping(consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<Product> create(
            @RequestPart("product") String productJson,
            @RequestPart("image") MultipartFile image) throws JsonProcessingException {

        // Convert JSON string to ProductModel using Jackson
        Product product = new ObjectMapper().readValue(productJson, Product.class);

        return new ResponseEntity<>(productService.createProduct(product, image), HttpStatus.CREATED);
    }

    @PutMapping(value = "/{id}", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public Product updateProduct(
            @PathVariable Long id,
            @RequestPart("product") String product,
            @RequestPart(value = "image", required = false) MultipartFile image) throws JsonProcessingException {

        Product productConverted = new ObjectMapper().readValue(product, Product.class);

        return productService.updateProductWithImage(id, productConverted, image);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAll() {
        productService.deleteAll();
        return ResponseEntity.noContent().build();
    }
}
