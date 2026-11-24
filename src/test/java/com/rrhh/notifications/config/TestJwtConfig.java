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
    /** custom:user_id del token "operador-grupo"; PlataformaAlcanceIT lo asigna a su grupo. */
    public static final String OPERADOR_GRUPO_ID = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee";
    /** Subject del token "operador-grupo-sin-claim" (sin custom:user_id): su fila se busca por cognito_sub. */
    public static final String OPERADOR_SIN_CLAIM_SUB = "operador-grupo-sin-claim";

    /**
     * Tokens: "admin-suspendida" (Admin de RRHH de TENANT_SUSPENDIBLE), "admin-plataforma" (sin tenant) y
     * "admin-plataforma-con-tenant" (AdminPlataforma con un custom:tenant_id antiguo), "operador-grupo",
     * "operador-sin-grupo" y "operador-grupo-sin-claim" (OperadorSaaS sin tenant); cualquier otro es el
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
            case "operador-grupo" -> Map.of(
                    "email", "operador.grupo@rrhh.local",
                    "custom:role", "OperadorSaaS",
                    "custom:user_id", OPERADOR_GRUPO_ID);
            case "operador-sin-grupo" -> Map.of(
                    "email", "operador.solo@rrhh.local",
                    "custom:role", "OperadorSaaS",
                    "custom:user_id", "dddddddd-0000-0000-0000-000000000001");
            case "operador-grupo-sin-claim" -> Map.of(
                    "email", "operador.grupo@rrhh.local",
                    "custom:role", "OperadorSaaS");
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
                .subject(OPERADOR_SIN_CLAIM_SUB.equals(token) ? OPERADOR_SIN_CLAIM_SUB : "test")
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
