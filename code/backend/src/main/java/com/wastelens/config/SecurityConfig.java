package com.wastelens.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.wastelens.security.filter.ApiCacheHeaderFilter;
import com.wastelens.security.filter.OwnerParamSecurityFilter;
import com.wastelens.security.filter.OwnerRateLimitFilter;
import com.wastelens.security.filter.RequestLimitFilter;
import com.wastelens.security.selector.WasteLensJwsKeySelector;
import com.wastelens.security.validator.WasteLensJwtValidator;
import com.wastelens.security.verifier.WasteLensJwsVerifierFactory;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${auth.jwks-uri}")
    private String jwksUri;

    @Value("${auth.issuer:}")
    private String issuer;

    @Value("${auth.audience:}")
    private String audience;

    private final Clock clock;

    public SecurityConfig(Clock clock) {
        this.clock = clock;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        RequestLimitFilter requestLimitFilter = new RequestLimitFilter();
        OwnerParamSecurityFilter ownerParamSecurityFilter = new OwnerParamSecurityFilter();
        OwnerRateLimitFilter ownerRateLimitFilter = new OwnerRateLimitFilter();
        ApiCacheHeaderFilter apiCacheHeaderFilter = new ApiCacheHeaderFilter();

        String authOrigin = extractOrigin(jwksUri);
        String cspDirectives = "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self' " 
                + authOrigin + "; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; img-src 'self'";

        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(cspDirectives))
                .contentTypeOptions(Customizer.withDefaults())
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/",
                    "/index.html",
                    "/favicon.ico",
                    "/assets/**",
                    "/login",
                    "/dashboard",
                    "/entries/**",
                    "/compare"
                ).permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            .exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(
                    (request, response, authException) -> {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        String requestId = UUID.randomUUID().toString();
                        String json = "{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\",\"requestId\":\"" + requestId + "\"}}";
                        response.getWriter().write(json);
                    },
                    new AntPathRequestMatcher("/api/**")
                )
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.decoder(jwtDecoder()))
            )
            // Add custom filters in order
            .addFilterBefore(requestLimitFilter, SecurityContextHolderFilter.class)
            .addFilterBefore(ownerParamSecurityFilter, BearerTokenAuthenticationFilter.class)
            .addFilterAfter(ownerRateLimitFilter, BearerTokenAuthenticationFilter.class)
            .addFilterAfter(apiCacheHeaderFilter, AuthorizationFilter.class);

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        try {
            URL jwkSetUrl = URI.create(jwksUri).toURL();
            JWKSource<SecurityContext> jwkSource = new RemoteJWKSet<>(jwkSetUrl);

            // Allow asymmetric algorithms only (EdDSA, ES256, RS256) - rejects HS256 and none (T-AUTH-10)
            Set<JWSAlgorithm> expectedJwsAlgs = Set.of(
                JWSAlgorithm.EdDSA,
                JWSAlgorithm.ES256,
                JWSAlgorithm.RS256
            );

            JWSKeySelector<SecurityContext> keySelector = new WasteLensJwsKeySelector(expectedJwsAlgs, jwkSource);
            DefaultJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
            jwtProcessor.setJWSKeySelector(keySelector);
            jwtProcessor.setJWSVerifierFactory(new WasteLensJwsVerifierFactory());

            NimbusJwtDecoder decoder = new NimbusJwtDecoder(jwtProcessor);
            decoder.setJwtValidator(new WasteLensJwtValidator(issuer, audience, Duration.ofSeconds(30), clock));
            return decoder;
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid JWKS URI: " + jwksUri, e);
        }
    }

    private String extractOrigin(String uriString) {
        if (uriString == null || uriString.isBlank()) {
            return "https://*.neon.tech https://*.supabase.co";
        }
        try {
            URI uri = URI.create(uriString);
            if (uri.getScheme() != null && uri.getHost() != null) {
                int port = uri.getPort();
                return uri.getScheme() + "://" + uri.getHost() + (port != -1 ? ":" + port : "");
            }
        } catch (Exception ignored) {}
        return "https://*.neon.tech https://*.supabase.co";
    }
}
