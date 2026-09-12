package ug.go.caa.recruitment.shared.integration;

import org.springframework.web.util.HtmlUtils;

/**
 * Table-based HTML layout for transactional mail (email-client safe).
 */
public final class RecruitmentEmailTemplate {

    private static final String NAVY = "#0B2E5F";
    private static final String NAVY_LIGHT = "#1A4A8A";
    private static final String BG = "#F4F6F9";
    private static final String MUTED = "#5C6B7A";
    private static final String BORDER = "#E2E8F0";

    private RecruitmentEmailTemplate() {
    }

    public static String layout(String preheader, String bodyHtml) {
        String safePreheader = escape(preheader);
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <meta name="color-scheme" content="light"/>
                  <meta name="supported-color-schemes" content="light"/>
                  <title>%s</title>
                  <!--[if mso]><style type="text/css">body,table,td{font-family:Arial,sans-serif!important;}</style><![endif]-->
                </head>
                <body style="margin:0;padding:0;background:%s;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#0F1C30;">
                  <div style="display:none;max-height:0;overflow:hidden;opacity:0;">%s</div>
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:%s;">
                    <tr><td align="center" style="padding:32px 16px;">
                      <table role="presentation" width="600" cellspacing="0" cellpadding="0" style="max-width:600px;width:100%%;">
                        <tr>
                          <td style="background:%s;border-radius:12px 12px 0 0;padding:28px 32px;text-align:center;">
                            <p style="margin:0;font-size:13px;letter-spacing:0.08em;text-transform:uppercase;color:rgba(255,255,255,0.85);">
                              Uganda Civil Aviation Authority
                            </p>
                            <h1 style="margin:8px 0 0;font-size:22px;font-weight:600;color:#ffffff;line-height:1.3;">
                              UCAA e&#8209;Recruitment Portal
                            </h1>
                          </td>
                        </tr>
                        <tr>
                          <td style="background:#ffffff;border-left:1px solid %s;border-right:1px solid %s;padding:32px 32px 28px;">
                            %s
                          </td>
                        </tr>
                        <tr>
                          <td style="background:#ffffff;border:1px solid %s;border-top:none;border-radius:0 0 12px 12px;padding:0 32px 28px;">
                            %s
                          </td>
                        </tr>
                      </table>
                      <p style="margin:24px 0 0;font-size:12px;line-height:1.5;color:%s;text-align:center;max-width:560px;">
                        This message was sent by the UCAA HR recruitment system. Please do not reply to this email.
                        For assistance, contact your HR team or visit the portal help section.
                      </p>
                    </td></tr>
                  </table>
                </body>
                </html>
                """
                .formatted(
                        safePreheader,
                        BG,
                        safePreheader,
                        BG,
                        NAVY,
                        BORDER,
                        BORDER,
                        bodyHtml,
                        footerBlock(),
                        BORDER,
                        MUTED);
    }

    public static String greeting(String firstName) {
        return paragraph("Hello <strong>" + escape(firstName) + "</strong>,");
    }

    public static String paragraph(String htmlSafeInner) {
        return """
                <p style="margin:0 0 16px;font-size:15px;line-height:1.65;color:#0F1C30;">%s</p>
                """
                .formatted(htmlSafeInner);
    }

    public static String heading(String text) {
        return """
                <h2 style="margin:0 0 16px;font-size:18px;font-weight:600;color:%s;line-height:1.35;">%s</h2>
                """
                .formatted(NAVY, escape(text));
    }

    public static String primaryButton(String label, String href) {
        String safeHref = escapeAttribute(href);
        String safeLabel = escape(label);
        return """
                <table role="presentation" cellspacing="0" cellpadding="0" style="margin:8px 0 20px;">
                  <tr>
                    <td style="border-radius:8px;background:%s;">
                      <a href="%s" target="_blank" rel="noopener noreferrer"
                         style="display:inline-block;padding:14px 28px;font-size:15px;font-weight:600;color:#ffffff;text-decoration:none;border-radius:8px;background:%s;">
                        %s
                      </a>
                    </td>
                  </tr>
                </table>
                """
                .formatted(NAVY_LIGHT, safeHref, NAVY_LIGHT, safeLabel);
    }

    public static String mutedNote(String text) {
        return """
                <p style="margin:16px 0 0;font-size:13px;line-height:1.55;color:%s;">%s</p>
                """
                .formatted(MUTED, escape(text));
    }

    public static String wrapCustomBody(String body) {
        if (body == null || body.isBlank()) {
            return paragraph("No message body was provided.");
        }
        String trimmed = body.strip();
        if (trimmed.startsWith("<!DOCTYPE") || trimmed.startsWith("<html")) {
            return trimmed;
        }
        if (trimmed.contains("<p") || trimmed.contains("<div") || trimmed.contains("<table")) {
            return """
                    <div style="font-size:15px;line-height:1.65;color:#0F1C30;">%s</div>
                    """
                    .formatted(trimmed);
        }
        return paragraph(escape(trimmed).replace("\n", "<br/>"));
    }

    private static String footerBlock() {
        return """
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="border-top:1px solid %s;padding-top:20px;">
                  <tr>
                    <td style="font-size:13px;line-height:1.5;color:%s;">
                      <strong style="color:%s;">Uganda Civil Aviation Authority</strong><br/>
                      Human Resources &mdash; Recruitment
                    </td>
                  </tr>
                </table>
                """
                .formatted(BORDER, MUTED, NAVY);
    }

    public static String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    private static String escapeAttribute(String value) {
        return escape(value).replace("&#39;", "'");
    }
}
