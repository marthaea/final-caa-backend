package ug.go.caa.recruitment.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.seed")
public record SeedProperties(
        String mode,
        boolean demo,
        boolean volume,
        boolean allowProduction
) {
    public SeedProperties {
        mode = mode == null ? "" : mode.trim().toLowerCase();
    }

    public boolean enabled() {
        return !mode.isBlank();
    }

    public boolean includeCore() {
        return "core".equals(mode) || "all".equals(mode);
    }

    public boolean includeDemo() {
        return "demo".equals(mode) || "all".equals(mode);
    }

    public boolean includeVolume() {
        return "volume".equals(mode) || ("all".equals(mode) && volume);
    }
}
