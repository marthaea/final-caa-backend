package ug.go.caa.recruitment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ug.go.caa.recruitment.shared.integration.RecruitmentEmailTemplate;

class RecruitmentEmailTemplateTest {

    @Test
    void layoutIncludesBrandingAndButton() {
        String html = RecruitmentEmailTemplate.layout(
                "Verify your email",
                RecruitmentEmailTemplate.greeting("Jane")
                        + RecruitmentEmailTemplate.primaryButton("Verify", "https://example.org/verify?token=abc"));
        assertThat(html).contains("Uganda Civil Aviation Authority");
        assertThat(html).contains("UCAA e&#8209;Recruitment Portal");
        assertThat(html).contains("#0B2E5F");
        assertThat(html).contains("Verify");
        assertThat(html).contains("https://example.org/verify?token=abc");
    }

    @Test
    void escapesPlainTextInParagraph() {
        String html = RecruitmentEmailTemplate.paragraph("<script>alert(1)</script>");
        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("&lt;script&gt;");
    }
}
