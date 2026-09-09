package ug.go.caa.recruitment.shared.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Service
public class TokenService {

    private static final String ISSUER = "caa-recruitment";

    private final WebSecurityProperties properties;
    private final JwtEncoder accessEncoder;
    private final JwtEncoder refreshEncoder;
    private final JwtDecoder refreshDecoder;
    private final Clock clock;

    @Autowired
    public TokenService(WebSecurityProperties properties) {
        this(properties, Clock.systemUTC());
    }

    TokenService(WebSecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        SecretKey accessKey = key(properties.accessTokenSecret(), "JWT_SECRET");
        SecretKey refreshKey = key(properties.refreshTokenSecret(), "JWT_REFRESH_SECRET");
        this.accessEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(accessKey));
        this.refreshEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(refreshKey));
        this.refreshDecoder = decoder(refreshKey);
    }

    public String accessToken(UserTokenDetails user) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("id", user.id());
        claims.put("email", user.email());
        claims.put("firstName", user.firstName());
        claims.put("lastName", user.lastName());
        claims.put("accountType", user.accountType());
        claims.put("adminRole", user.adminRole());
        claims.put("employeeNumber", user.employeeNumber());
        claims.put("effectiveType", user.effectiveType());
        return encode(accessEncoder, claims, properties.accessTokenTtl());
    }

    public String refreshToken(UserTokenDetails user, long sessionId) {
        return encode(
                refreshEncoder,
                Map.of(
                        "id", user.id(),
                        "email", user.email(),
                        "tv", user.tokenVersion(),
                        "sid", sessionId),
                properties.refreshTokenTtl());
    }

    public Jwt decodeRefreshToken(String token) {
        return refreshDecoder.decode(token);
    }

    public Instant refreshTokenExpiresAt() {
        return clock.instant().plus(properties.refreshTokenTtl());
    }

    public JwtDecoder accessTokenDecoder() {
        return decoder(key(properties.accessTokenSecret(), "JWT_SECRET"));
    }

    private String encode(JwtEncoder encoder, Map<String, Object> customClaims, java.time.Duration ttl) {
        Instant issuedAt = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(ttl));
        customClaims.forEach((name, value) -> {
            if (value != null) {
                claims.claim(name, value);
            }
        });
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    private static JwtDecoder decoder(SecretKey key) {
        return NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    private static SecretKey key(String value, String environmentName) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(environmentName + " must contain at least 32 bytes");
        }
        return new SecretKeySpec(value.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public record UserTokenDetails(
            long id,
            String email,
            String firstName,
            String lastName,
            String accountType,
            String adminRole,
            String employeeNumber,
            String effectiveType,
            int tokenVersion
    ) {
    }
}
