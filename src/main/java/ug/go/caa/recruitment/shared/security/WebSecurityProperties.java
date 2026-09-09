package ug.go.caa.recruitment.shared.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security")
public record WebSecurityProperties(
        List<String> allowedOrigins,
        String accessTokenSecret,
        String refreshTokenSecret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        boolean secureCookie,
        String sameSite
) {

    public WebSecurityProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        accessTokenTtl = accessTokenTtl == null ? Duration.ofHours(2) : accessTokenTtl;
        refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(7) : refreshTokenTtl;
        sameSite = sameSite == null ? "None" : sameSite;
    }
}
