package com.warsha.erp.services;

import com.warsha.erp.dtos.OrderRequest;
import com.warsha.erp.entities.Order;
import com.warsha.erp.entities.OrderItems;
import com.warsha.erp.entities.Product;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class EmailService {

    private static final String FROM = "elwarsha77a@gmail.com";
    private static final String FROM_NAME = "ورشة | El Warsha";
    private static final String ORDERS_INBOX = "ahmednaser77a@gmail.com";
    private static final ClassPathResource LOGO = new ClassPathResource("email/logo.png");

    @Autowired
    private final JavaMailSender javaMailSender;

    @Value("${image.upload.dir:/root/warsha_images/}")
    private String imageUploadDir;

    // Formatter for consistent log timestamps
    private final DateTimeFormatter logFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    public EmailService(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    private String getTimestamp() {
        return LocalDateTime.now().format(logFormat);
    }

    public String generateOtp() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    public String sendOtpEmail(String toEmail) {
        String otp = generateOtp();
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(FROM, FROM_NAME);
            helper.setTo(toEmail);
            helper.setSubject("رمز التحقق الخاص بك - ورشة");
            helper.setText(EmailTemplates.otp(otp), true);
            addLogo(helper);

            javaMailSender.send(message);
            System.out.println("[" + getTimestamp() + "] SUCCESS: OTP sent to " + toEmail);
            return otp;

        } catch (Exception e) {
            System.err.println("[" + getTimestamp() + "] ERROR: Failed to send OTP email: " + e.getMessage());
            return null;
        }
    }

    public List<String> saveImagesToTemp(List<MultipartFile> files) throws IOException {
        System.out.println("[" + getTimestamp() + "] INFO: Processing " + (files != null ? files.size() : 0) + " files for temporary storage");
        List<String> tempPaths = new ArrayList<>();

        if (files == null || files.isEmpty()) {
            return tempPaths;
        }

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                System.out.println("[" + getTimestamp() + "] WARN: Skipping empty file upload");
                continue;
            }

            String originalName = file.getOriginalFilename();
            String extension = ".tmp";
            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }

            Path tempFile = Files.createTempFile("order_upload_", extension);
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);

            String absolutePath = tempFile.toFile().getAbsolutePath();
            System.out.println("[" + getTimestamp() + "] INFO: File saved to temp: " + absolutePath);
            tempPaths.add(absolutePath);
        }

        return tempPaths;
    }

    public void sendNewOrderNotification(Order order, OrderRequest request, List<String> imagePaths) {
        String orderId = String.valueOf(order.getId());
        System.out.println("[" + getTimestamp() + "] INFO: Preparing email notification for Order ID: " + orderId);

        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(FROM, FROM_NAME);
            helper.setTo(ORDERS_INBOX);
            if (order.getCustomer().getEmail() != null && !order.getCustomer().getEmail().isBlank()) {
                helper.setReplyTo(order.getCustomer().getEmail());
            }
            helper.setSubject("🛍️ طلب جديد #" + orderId + " - " + order.getCustomer().getFullName()
                    + " - " + EmailTemplates.plainMoney(order.getTotalPrice()));

            List<FileSystemResource> attachments = new ArrayList<>();
            if (imagePaths != null) {
                for (String path : imagePaths) {
                    FileSystemResource file = new FileSystemResource(new File(path));
                    if (file.exists()) {
                        attachments.add(file);
                    } else {
                        System.out.println("[" + getTimestamp() + "] WARN: Attachment missing at path: " + path);
                    }
                }
            }

            // Product photos stored on this server are embedded next to each item
            Map<Long, FileSystemResource> productImages = new LinkedHashMap<>();
            for (OrderItems item : order.getItems()) {
                FileSystemResource image = localProductImage(item.getProduct());
                if (image != null) productImages.putIfAbsent(item.getProduct().getProductID(), image);
            }
            Map<Long, String> productImageCids = new HashMap<>();
            productImages.keySet().forEach(id -> productImageCids.put(id, "product" + id));

            helper.setText(
                    EmailTemplates.newOrderAdminText(order, request, attachments.size()),
                    EmailTemplates.newOrderAdmin(order, request, productImageCids, attachments.size(), LocalDateTime.now()));
            addLogo(helper);
            for (Map.Entry<Long, FileSystemResource> e : productImages.entrySet()) {
                helper.addInline(productImageCids.get(e.getKey()), e.getValue());
            }
            for (FileSystemResource file : attachments) {
                helper.addAttachment(file.getFilename(), file);
            }

            javaMailSender.send(message);
            System.out.println("[" + getTimestamp() + "] SUCCESS: Email sent for Order " + orderId + " with " + attachments.size() + " attachments.");

        } catch (MessagingException e) {
            System.out.println("[" + getTimestamp() + "] ERROR: MessagingException while sending email: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("[" + getTimestamp() + "] ERROR: Unexpected error during email process: " + e.getMessage());
        }
    }

    public void sendOrderProcessingEmail(String customerEmail, String customerName, String orderId, double orderTotal) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(FROM, FROM_NAME);
            helper.setTo(customerEmail);
            helper.setSubject("نحن نجهز طلبك بكل عناية ✨ رقم الطلب #" + orderId);
            helper.setText(EmailTemplates.orderProcessing(customerName, orderId, orderTotal), true);
            addLogo(helper);

            javaMailSender.send(message);
            System.out.println("SUCCESS: Processing email sent to " + customerEmail);

        } catch (Exception e) {
            System.err.println("ERROR: Failed to send processing email: " + e.getMessage());
        }
    }

    public void sendOrderCancellationEmail(String customerEmail, String customerName, String orderId) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(FROM, FROM_NAME);
            helper.setTo(customerEmail);
            helper.setSubject("تحديث بخصوص طلبك رقم #" + orderId);
            helper.setText(EmailTemplates.orderCancelled(customerName, orderId), true);
            addLogo(helper);

            javaMailSender.send(message);
            System.out.println("SUCCESS: Cancellation email sent to " + customerEmail);

        } catch (Exception e) {
            System.err.println("ERROR: Failed to send cancellation email: " + e.getMessage());
        }
    }

    private void addLogo(MimeMessageHelper helper) throws MessagingException {
        helper.addInline(EmailTemplates.LOGO_CID, LOGO, "image/png");
    }

    /** DB value "/images/abc.jpg" -> file in image.upload.dir (see ImageConfig); Drive-hosted images are skipped. */
    private FileSystemResource localProductImage(Product product) {
        String url = product == null ? null : product.getImageUrl();
        if (url == null || !url.startsWith("/images/")) return null;
        FileSystemResource file = new FileSystemResource(Paths.get(imageUploadDir, url.substring("/images/".length())));
        return file.exists() ? file : null;
    }
}
