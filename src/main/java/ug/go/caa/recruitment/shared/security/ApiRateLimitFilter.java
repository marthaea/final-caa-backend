package ug.go.caa.recruitment.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import ug.go.caa.recruitment.shared.web.ApiResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public ApiRateLimitFilter(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        String client = digest(request.getRemoteAddr());
        Limit endpointLimit = endpointLimit(request);
        if (!allow(client, new Limit("general", 300, Duration.ofMinutes(15)))) {
            reject(response, "Too many requests. Please slow down.");
            return;
        }
        if (endpointLimit != null && !allow(client, endpointLimit)) {
            reject(response, endpointLimit.message());
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean allow(String client, Limit limit) {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minus(limit.window());
        Integer count = jdbc.sql("""
                INSERT INTO api_rate_limits (client_key, bucket, window_start, request_count)
                VALUES (:client, :bucket, now(), 1)
                ON CONFLICT (client_key, bucket) DO UPDATE SET
                    window_start = CASE
                        WHEN api_rate_limits.window_start <= :cutoff THEN now()
                        ELSE api_rate_limits.window_start
                    END,
                    request_count = CASE
                        WHEN api_rate_limits.window_start <= :cutoff THEN 1
                        ELSE api_rate_limits.request_count + 1
                    END
                RETURNING request_count
                """)
                .param("client", client)
                .param("bucket", limit.bucket())
                .param("cutoff", cutoff)
                .query(Integer.class)
                .single();
        return count <= limit.maximum();
    }

    private Limit endpointLimit(HttpServletRequest request) {
        String methodPath = request.getMethod() + " " + request.getServletPath();
        if ("POST /api/auth/forgot-password".equals(methodPath)) {
            return new Limit(
                    "forgot-password",
                    3,
                    Duration.ofHours(1),
                    "Too many password reset requests. Please try again later.");
        }
        if (methodPath.matches("POST /api/auth/(register|login|reset-password|resend-verification)")) {
            return new Limit(
                    "auth",
                    10,
                    Duration.ofMinutes(15),
                    "Too many attempts. Please try again in 15 minutes.");
        }
        if ("POST /api/analytics/event".equals(methodPath)) {
            return new Limit(
                    "analytics",
                    10,
                    Duration.ofMinutes(1),
                    "Too many requests");
        }
        return null;
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), ApiResponse.failure(message));
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record Limit(String bucket, int maximum, Duration window, String message) {
        private Limit(String bucket, int maximum, Duration window) {
            this(bucket, maximum, window, "");
        }
    }
}
