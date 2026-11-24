package com.rrhh.notifications.config;

import com.rrhh.notifications.security.JwtTenantConverter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@TestConfiguration
public class TestJwtConfig {
    /** Empresa del token "admin-suspendida"; TenantSuspendidoIT la crea en identity_tenant y la suspende. */
    public static final String TENANT_SUSPENDIBLE = "99999999-9999-9999-9999-999999999999";

    /**
     * Tokens: "admin-suspendida" (Admin de RRHH de TENANT_SUSPENDIBLE), "admin-plataforma" (sin tenant) y
     * "admin-plataforma-con-tenant" (AdminPlataforma con un custom:tenant_id antiguo); cualquier otro es el
     * Admin de RRHH del tenant demo.
     */
    static Map<String, Object> claims(String token) {
        return switch (token) {
            case "admin-suspendida" -> Map.of(
                    "email", "admin.suspendida@rrhh.local",
                    "custom:tenant_id", TENANT_SUSPENDIBLE,
                    "custom:role", "Admin de RRHH",
                    "custom:user_id", "99999999-0000-0000-0000-000000000001");
            case "admin-plataforma" -> Map.of(
                    "email", "owner@gmail.com",
                    "custom:role", "AdminPlataforma");
            case "admin-plataforma-con-tenant" -> Map.of(
                    "email", "owner@gmail.com",
                    "custom:tenant_id", TENANT_SUSPENDIBLE,
                    "custom:role", "AdminPlataforma");
            default -> Map.of(
                    "email", "admin.demo@rrhh.local",
                    "custom:tenant_id", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "custom:role", "Admin de RRHH",
                    "custom:user_id", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        };
    }

    @Bean
    @Primary
    JwtDecoder jwtDecoder() {
        return token -> Jwt.withTokenValue(token)
                .header("alg", "none")
                .subject("test")
                .claims(c -> c.putAll(claims(token)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    OncePerRequestFilter testTenantContextFilter(JwtTenantConverter converter) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                    converter.convert(jwtAuth.getToken());
                }
                filterChain.doFilter(request, response);
            }
        };
    }
}
