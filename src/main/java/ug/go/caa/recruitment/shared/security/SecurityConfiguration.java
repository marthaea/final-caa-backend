package ug.go.caa.recruitment.shared.security;

import tools.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import ug.go.caa.recruitment.shared.web.ApiResponse;
import ug.go.caa.recruitment.shared.web.RequestCorrelationFilter;

@Configuration
public class SecurityConfiguration {

    private static final String[] PUBLIC_PATHS = {
            "/ping",
            "/actuator/health",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper mapper,
            TokenService tokenService
    ) throws Exception {
        DefaultBearerTokenResolver bearerTokens = new DefaultBearerTokenResolver();
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh-token",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/analytics/event",
                                "/api/chatbot/queries").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/auth/verify-email",
                                "/api/criteria/*/public",
                                "/api/settings",
                                "/api/staff/verify/**",
                                "/api/cv/files/**",
                                "/api/permissions/roles/defaults").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/jobs", "/api/jobs/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(request ->
                                "GET".equals(request.getMethod())
                                        && request.getServletPath().matches("/api/jobs(?:/\\d+)?")
                                        ? null
                                        : bearerTokens.resolve(request))
                        .jwt(jwt -> jwt.decoder(tokenService.accessTokenDecoder()))
                        .authenticationEntryPoint((request, response, exception) ->
                                writeAuthenticationError(response, mapper)))
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                    writeAuthenticationError(response, mapper);
                }))
                .build();
    }

    private static void writeAuthenticationError(
            jakarta.servlet.http.HttpServletResponse response,
            ObjectMapper mapper
    ) throws java.io.IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), ApiResponse.failure("Unauthorised"));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(WebSecurityProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        List<String> origins = properties.allowedOrigins();
        if (origins.stream().anyMatch(origin -> "*".equals(origin) || origin.contains("*"))) {
            cors.setAllowedOriginPatterns(origins.isEmpty() ? List.of("*") : origins);
        } else {
            cors.setAllowedOrigins(origins);
        }
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", RequestCorrelationFilter.HEADER));
        cors.setExposedHeaders(List.of(RequestCorrelationFilter.HEADER));
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
