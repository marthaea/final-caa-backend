package ug.go.caa.recruitment.shared.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.integrations")
public record IntegrationProperties(
        String frontendUrl,
        Mail mail,
        Cloudinary cloudinary
) {
    public IntegrationProperties {
        frontendUrl = blank(frontendUrl) ? "http://localhost:3000" : frontendUrl;
        mail = mail == null ? new Mail(false, null, null) : mail;
        cloudinary = cloudinary == null ? new Cloudinary(null, null, null) : cloudinary;
    }

    public record Mail(boolean enabled, String from, String senderName) {
    }

    public record Cloudinary(String cloudName, String apiKey, String apiSecret) {
        public boolean configured() {
            return !blank(cloudName) && !blank(apiKey) && !blank(apiSecret);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
