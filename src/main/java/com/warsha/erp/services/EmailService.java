package com.warsha.erp.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class EmailService {

    @Autowired
    private final JavaMailSender javaMailSender;

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

            helper.setFrom("elwarsha77a@gmail.com");
            helper.setTo(toEmail);
            helper.setSubject("رمز التحقق الخاص بك - OTP");

            String htmlContent = """
            <div dir="rtl" style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; line-height: 1.6; color: #444; max-width: 600px; margin: 0 auto; border: 1px solid #eee; padding: 25px; text-align: right;">
                <h2 style="color: #333; border-bottom: 2px solid #f4e4e4; padding-bottom: 10px;">رمز التحقق</h2>
            
                <p>استخدم رمز التحقق التالي لإكمال إجراءات التحقق الخاصة بك:</p>
            
                <div style="background-color: #fafafa; padding: 20px; border-radius: 8px; margin: 20px 0; border-right: 4px solid #d4a373; text-align: center;">
                    <h1 style="margin: 0; color: #d4a373; letter-spacing: 5px;">%s</h1>
                </div>
            
                <p>هذا الرمز صالح لفترة محدودة. لا تشارك هذا الرمز مع أي شخص.</p>
            
                <p style="margin-top: 30px;">شكراً لك،<br/><strong>فريق عمل ورشة</strong></p>
            
                <hr style="border: 0; border-top: 1px solid #eee; margin: 20px 0;" />
                <p style="font-size: 12px; color: #999;">إذا لم تطلب هذا الرمز، يرجى تجاهل هذا البريد الإلكتروني.</p>
            </div>
            """.formatted(otp);

            helper.setText(htmlContent, true);

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

    public void sendNewOrderNotification(String customerName, String orderId, double orderTotal, List<String> imagePaths) {
        System.out.println("[" + getTimestamp() + "] INFO: Preparing email notification for Order ID: " + orderId);

        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setFrom("elwarsha77a@gmail.com");
            helper.setTo("ekhalaf990@gmail.com");
//            helper.setTo("ahmednaser77a@gmail.com");
            helper.setSubject("New Order Received! (ID: " + orderId + ")");

            String emailBody = String.format("""
            Hello Ahmed,
            
            Great news! You have received a new order.
            
            --------------------------------
            Order ID:      %s
            Customer Name: %s
            Total Amount:  EGP %.2f
            --------------------------------
            
            See the attached images for order details.
            """, orderId, customerName, orderTotal);

            helper.setText(emailBody);

            int attachmentCount = 0;
            if (imagePaths != null && !imagePaths.isEmpty()) {
                for (String path : imagePaths) {
                    FileSystemResource file = new FileSystemResource(new File(path));

                    if (file.exists()) {
                        helper.addAttachment(file.getFilename(), file);
                        attachmentCount++;
                    } else {
                        System.out.println("[" + getTimestamp() + "] WARN: Attachment missing at path: " + path);
                    }
                }
            }

            javaMailSender.send(message);
            System.out.println("[" + getTimestamp() + "] SUCCESS: Email sent for Order " + orderId + " with " + attachmentCount + " attachments.");

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

            helper.setFrom("elwarsha77a@gmail.com");
            helper.setTo(customerEmail); // Now sending to the actual customer
            helper.setSubject("نحن نجهز طلبك بكل عناية ✨ رقم الطلب #" + orderId);

            String htmlContent = """
            <div dir="rtl" style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; line-height: 1.6; color: #444; max-width: 600px; margin: 0 auto; border: 1px solid #eee; padding: 25px; text-align: right;">
                <h2 style="color: #333; border-bottom: 2px solid #f4e4e4; padding-bottom: 10px;">شكراً لطلبك من ورشة، %s</h2>
            
                <p>يسعدنا إبلاغك أننا استلمنا طلبك رقم <strong>#%s</strong>، وفريقنا يعمل الآن على مراجعته وتجهيزه.</p>
            
                <div style="background-color: #fafafa; padding: 20px; border-radius: 8px; margin: 20px 0; border-right: 4px solid #d4a373;">
                    <h3 style="margin-top: 0; color: #d4a373;">تفاصيل الطلب:</h3>
                    <p style="margin: 5px 0;"><strong>رقم الطلب:</strong> %s</p>
                    <p style="margin: 5px 0;"><strong>الإجمالي:</strong> %.2f جنيه مصري</p>
                    <p style="margin: 5px 0;"><strong>الحالة:</strong> قيد المراجعة</p>
                </div>
            
                <div style="background-color: #fff9f9; border: 1px solid #f2dede; color: #a94442; padding: 15px; border-radius: 8px; margin: 20px 0; font-size: 14px;">
                    <strong>ملاحظة هامة:</strong>
                    يرجى العلم أنه يجب تأكيد الطلب من خلال سداد "العربون". في حال عدم استلام مبلغ العربون خلال المدة المحددة، سيتم إلغاء الطلب تلقائياً من قِبل النظام.
                </div>
            
                <p>بمجرد تأكيد الدفع وشحن الطلب، سنقوم بإرسال رسالة أخرى لتتبع الشحنة.</p>
            
                <p style="margin-top: 30px;">شكراً لاختيارك <strong>ورشة</strong>.</p>
            
                <div style="text-align: center; margin-top: 30px;">
                <a href="https://el-warsha-accessories.web.app" style="background-color: #d4a373; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px; font-weight: bold;">تسوقي مرة أخرى</a>
                </div>
            
                <hr style="border: 0; border-top: 1px solid #eee; margin: 20px 0;" />
                <p style="font-size: 12px; color: #999;">إذا كان لديك أي استفسار حول كيفية سداد العربون أو تفاصيل طلبك، يمكنك الرد مباشرة على هذا البريد.</p>
            </div>
            """.formatted(customerName, orderId, orderId, orderTotal);

            helper.setText(htmlContent, true); // The 'true' flag enables HTML rendering

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

            helper.setFrom("elwarsha77a@gmail.com");
            helper.setTo(customerEmail);
            helper.setSubject("تحديث بخصوص طلبك رقم #" + orderId);

            String htmlContent = """
        <div dir="rtl" style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; line-height: 1.6; color: #444; max-width: 600px; margin: 0 auto; border: 1px solid #eee; padding: 25px; text-align: right;">
            <h2 style="color: #888; border-bottom: 2px solid #eee; padding-bottom: 10px;">تم إلغاء طلبك، %s</h2>
        
            <p>نحيطك علماً بأنه تم إلغاء طلبك رقم <strong>#%s</strong> بنجاح.</p>
        
            <div style="background-color: #fcfcfc; padding: 20px; border-radius: 8px; margin: 20px 0; border-right: 4px solid #888;">
                <p style="margin: 5px 0;"><strong>رقم الطلب:</strong> %s</p>
                <p style="margin: 5px 0;"><strong>حالة الطلب:</strong> ملغي</p>
            </div>
        
            <p>غالباً ما يتم ذلك بسبب عدم استلام مبلغ العربون خلال المدة المحددة، أو بناءً على طلبك الشخصي.</p>
        
            <p style="margin-top: 20px;">نأمل أن نراكِ مجدداً في <strong>ورشة</strong> قريباً، قطعنا المميزة دائماً بانتظارك!</p>
        
            <div style="text-align: center; margin-top: 30px;">
                <a href="https://el-warsha-accessories.web.app" style="background-color: #d4a373; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px; font-weight: bold;">تسوقي مرة أخرى</a>
            </div>
        
            <hr style="border: 0; border-top: 1px solid #eee; margin: 30px 0 20px 0;" />
            <p style="font-size: 12px; color: #999;">إذا تم الإلغاء عن طريق الخطأ أو قمتِ بالفعل بتحويل العربون، يرجى التواصل معنا فوراً عبر الرد على هذا البريد.</p>
        </div>
        """.formatted(customerName, orderId, orderId);

            helper.setText(htmlContent, true);
            javaMailSender.send(message);
            System.out.println("SUCCESS: Cancellation email sent to " + customerEmail);

        } catch (Exception e) {
            System.err.println("ERROR: Failed to send cancellation email: " + e.getMessage());
        }
    }
}
