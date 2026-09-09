package ug.go.caa.recruitment.shared.integration;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ug.go.caa.recruitment.shared.web.ApiException;

@Service
public class CloudinaryUploadService {

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final IntegrationProperties properties;

    public CloudinaryUploadService(IntegrationProperties properties) {
        this.properties = properties;
    }

    public boolean configured() {
        return properties.cloudinary().configured();
    }

    public UploadResult upload(MultipartFile file, boolean photo, String ownerEmail) {
        validate(file);
        if (!configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Cloudinary is not configured");
        }
        boolean image = file.getContentType() != null && file.getContentType().startsWith("image/");
        boolean asPhoto = photo || image;
        String publicId = ownerEmail.replaceAll("[^a-zA-Z0-9]", "_") + "_" + System.currentTimeMillis();
        Map<String, Object> options = asPhoto
                ? ObjectUtils.asMap(
                        "folder", "caa-recruitment/photos",
                        "public_id", publicId,
                        "resource_type", "image",
                        "transformation", new Transformation<>()
                                .width(400).height(400).crop("fill").fetchFormat("webp"))
                : ObjectUtils.asMap(
                        "folder", "caa-recruitment/documents",
                        "public_id", publicId,
                        "resource_type", "auto");
        try {
            Map<?, ?> result = cloudinary().uploader().upload(file.getBytes(), options);
            return new UploadResult(
                    String.valueOf(result.get("secure_url")),
                    String.valueOf(result.get("public_id")),
                    String.valueOf(result.get("format")),
                    ((Number) result.get("bytes")).longValue(),
                    asPhoto);
        } catch (IOException exception) {
            throw new IllegalStateException("Cloudinary upload failed", exception);
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No file uploaded");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "File size must not exceed 5 MB");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Only JPEG, PNG, WebP, PDF, and Word files are allowed");
        }
    }

    private Cloudinary cloudinary() {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", properties.cloudinary().cloudName(),
                "api_key", properties.cloudinary().apiKey(),
                "api_secret", properties.cloudinary().apiSecret(),
                "secure", true));
    }

    public record UploadResult(
            String url,
            String publicId,
            String format,
            long bytes,
            boolean photo
    ) {
    }
}
