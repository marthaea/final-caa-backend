package ug.go.caa.recruitment.feature.recruitment.application;

import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ug.go.caa.recruitment.shared.integration.CloudinaryUploadService;
import ug.go.caa.recruitment.shared.integration.CloudinaryUploadService.UploadResult;
import ug.go.caa.recruitment.shared.integration.LocalUploadService;
import ug.go.caa.recruitment.shared.persistence.AuditWriter;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;

@Service
public class CvUploadService {

    private final CloudinaryUploadService cloudinary;
    private final LocalUploadService localUploads;
    private final JdbcClient jdbc;
    private final AuditWriter audit;

    public CvUploadService(
            CloudinaryUploadService cloudinary,
            LocalUploadService localUploads,
            JdbcClient jdbc,
            AuditWriter audit
    ) {
        this.cloudinary = cloudinary;
        this.localUploads = localUploads;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional
    public FileResponse upload(AuthenticatedActor actor, MultipartFile file, String type) {
        boolean photo = "photo".equals(type);
        UploadResult uploaded = cloudinary.configured()
                ? cloudinary.upload(file, photo, actor.email())
                : localUploads.upload(file, photo);
        if (uploaded.photo()) {
            int updated = jdbc.sql("""
                    UPDATE cv_profiles
                    SET photo_url = :url,
                        user_id = COALESCE(user_id, :userId),
                        updated_at = now()
                    WHERE lower(user_email) = lower(:email)
                    """)
                    .param("url", uploaded.url())
                    .param("userId", actor.id())
                    .param("email", actor.email())
                    .update();
            if (updated == 0) {
                jdbc.sql("""
                        INSERT INTO cv_profiles (user_id, user_email, photo_url)
                        VALUES (:userId, lower(:email), :url)
                        """)
                        .param("userId", actor.id())
                        .param("email", actor.email())
                        .param("url", uploaded.url())
                        .update();
            }
        }
        audit.write(actor, "File uploaded", uploaded.publicId());
        return new FileResponse(
                uploaded.url(), uploaded.publicId(), uploaded.format(), uploaded.bytes());
    }

    public Resource loadLocalFile(String filename) {
        return localUploads.load(filename);
    }

    public record FileResponse(String url, String publicId, String format, long bytes) {
    }
}
