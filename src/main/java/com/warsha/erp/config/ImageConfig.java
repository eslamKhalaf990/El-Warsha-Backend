package com.warsha.erp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

// Serves product images saved on disk: DB value "/images/abc.jpg" -> <image.upload.dir>/abc.jpg
@Configuration
public class ImageConfig implements WebMvcConfigurer {

    @Value("${image.upload.dir:/root/warsha_images/}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/images/**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/");
    }
}
