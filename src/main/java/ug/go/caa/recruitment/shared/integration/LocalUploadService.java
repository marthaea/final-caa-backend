package ug.go.caa.recruitment.shared.integration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ug.go.caa.recruitment.shared.integration.CloudinaryUploadService.UploadResult;
import ug.go.caa.recruitment.shared.web.ApiException;

/**
 * Dev/local fallback when Cloudinary credentials are absent.
 * Stores files under {@code uploads/} and serves them via {@code /api/cv/files/{name}}.
 */
@Service
public class LocalUploadService {

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "application/pdf", "pdf",
            "application/msword", "doc",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");

    private final Path root;

    public LocalUploadService(
            @org.springframework.beans.factory.annotation.Value("${app.upload-dir:uploads}") String uploadDir
    ) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public UploadResult upload(MultipartFile file, boolean photo) {
        validate(file);
        boolean image = file.getContentType() != null && file.getContentType().startsWith("image/");
        boolean asPhoto = photo || image;
        String extension = EXTENSIONS.getOrDefault(file.getContentType(), "bin");
        String publicId = UUID.randomUUID().toString().replace("-", "");
        String filename = publicId + "." + extension;
        try {
            Files.createDirectories(root);
            Path target = root.resolve(filename).normalize();
            if (!target.startsWith(root)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid upload path");
            }
            Files.write(target, file.getBytes());
            return new UploadResult(
                    "/api/cv/files/" + filename,
                    publicId,
                    extension,
                    file.getSize(),
                    asPhoto);
        } catch (IOException exception) {
            throw new IllegalStateException("Local upload failed", exception);
        }
    }

    public Resource load(String filename) {
        String safe = filename == null ? "" : filename.trim();
        if (!safe.matches("[a-zA-Z0-9._-]+")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid file name");
        }
        try {
            Path path = root.resolve(safe).normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path)) {
                throw new ApiException(HttpStatus.NOT_FOUND, "File not found");
            }
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "File not found");
            }
            return resource;
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "File not found");
        }
    }

    public static String contentTypeFor(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        return "application/octet-stream";
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
}
