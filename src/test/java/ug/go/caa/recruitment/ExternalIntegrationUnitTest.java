package ug.go.caa.recruitment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import ug.go.caa.recruitment.shared.integration.CloudinaryUploadService;
import ug.go.caa.recruitment.shared.integration.IntegrationProperties;
import ug.go.caa.recruitment.shared.integration.IntegrationProperties.Cloudinary;
import ug.go.caa.recruitment.shared.integration.IntegrationProperties.Mail;
import ug.go.caa.recruitment.shared.web.ApiException;

class ExternalIntegrationUnitTest {

    private final CloudinaryUploadService uploads = new CloudinaryUploadService(
            new IntegrationProperties(
                    "https://recruitment.example.org",
                    new Mail(false, null, null),
                    new Cloudinary(null, null, null)));

    @Test
    void rejectsMissingOversizedAndUnsupportedUploadsBeforeCallingCloudinary() {
        assertUploadError(new MockMultipartFile("file", new byte[0]), "No file uploaded");
        assertUploadError(
                new MockMultipartFile("file", "payload.exe", "application/octet-stream", new byte[]{1}),
                "Only JPEG, PNG, WebP, PDF, and Word files are allowed");
        assertUploadError(
                new MockMultipartFile(
                        "file", "large.pdf", "application/pdf", new byte[5 * 1024 * 1024 + 1]),
                "File size must not exceed 5 MB");
    }

    @Test
    void reportsUnavailableWhenCloudinaryCredentialsAreAbsent() {
        MockMultipartFile file =
                new MockMultipartFile("file", "cv.pdf", "application/pdf", new byte[]{1});

        assertThatThrownBy(() -> uploads.upload(file, false, "candidate@example.org"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception).hasMessage("Cloudinary is not configured");
                });
    }

    private void assertUploadError(MockMultipartFile file, String message) {
        assertThatThrownBy(() -> uploads.upload(file, false, "candidate@example.org"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception).hasMessage(message);
                });
    }
}
