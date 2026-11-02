package com.wastelens.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.wastelens.security.jwks.TestJwksServer;
import com.wastelens.security.selector.WasteLensJwsKeySelector;
import com.wastelens.security.validator.WasteLensJwtValidator;
import com.wastelens.security.verifier.WasteLensJwsVerifierFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.net.URI;
import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuthValidationTest {

    private static TestJwksServer jwksServer;
    private static JwtDecoder jwtDecoder;
    private static final String ISSUER = "https://auth.wastelens.test";
    private static final String AUDIENCE = "authenticated";

    @BeforeAll
    static void setup() throws Exception {
        jwksServer = new TestJwksServer();

        URL jwkSetUrl = URI.create(jwksServer.getJwksUri()).toURL();
        JWKSource<SecurityContext> jwkSource = new RemoteJWKSet<>(jwkSetUrl);

        Set<JWSAlgorithm> expectedJwsAlgs = Set.of(
            JWSAlgorithm.ES256,
            JWSAlgorithm.EdDSA,
            JWSAlgorithm.RS256
        );

        JWSKeySelector<SecurityContext> keySelector = new WasteLensJwsKeySelector(expectedJwsAlgs, jwkSource);
        DefaultJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
        jwtProcessor.setJWSKeySelector(keySelector);
        jwtProcessor.setJWSVerifierFactory(new WasteLensJwsVerifierFactory());

        NimbusJwtDecoder decoder = new NimbusJwtDecoder(jwtProcessor);
        decoder.setJwtValidator(new WasteLensJwtValidator(ISSUER, AUDIENCE, Duration.ofSeconds(5), Clock.systemUTC()));
        jwtDecoder = decoder;
    }

    @AfterAll
    static void teardown() {
        if (jwksServer != null) {
            jwksServer.close();
        }
    }

    private JWTClaimsSet.Builder createValidClaimsBuilder() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .audience(AUDIENCE)
            .subject(UUID.randomUUID().toString())
            .issueTime(Date.from(now.minusSeconds(10)))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .claim("role", "authenticated")
            .claim("is_anonymous", false);
    }

    @Test
    @DisplayName("T-AUTH-02: Expired token is rejected")
    void tAuth02_expiredTokenRejected() throws Exception {
        Instant past = Instant.now().minusSeconds(60);
        JWTClaimsSet claims = createValidClaimsBuilder()
            .expirationTime(Date.from(past))
            .build();

        String token = jwksServer.createSignedToken(claims);
        assertThrows(JwtException.class, () -> jwtDecoder.decode(token));
    }

    @Test
    @DisplayName("T-AUTH-03: Not-yet-valid token (nbf in future) is rejected")
    void tAuth03_notYetValidTokenRejected() throws Exception {
        Instant future = Instant.now().plusSeconds(60);
        JWTClaimsSet claims = createValidClaimsBuilder()
            .notBeforeTime(Date.from(future))
            .build();

        String token = jwksServer.createSignedToken(claims);
        assertThrows(JwtException.class, () -> jwtDecoder.decode(token));
    }

    @Test
    @DisplayName("T-AUTH-04: Tampered signature is rejected")
    void tAuth04_tamperedSignatureRejected() throws Exception {
        JWTClaimsSet claims = createValidClaimsBuilder().build();
        String tamperedToken = jwksServer.createTamperedToken(claims);

        assertThrows(JwtException.class, () -> jwtDecoder.decode(tamperedToken));
    }

    @Test
    @DisplayName("T-AUTH-05: Wrong issuer is rejected")
    void tAuth05_wrongIssuerRejected() throws Exception {
        JWTClaimsSet claims = createValidClaimsBuilder()
            .issuer("https://near-miss.auth.wastelens.test")
            .build();

        String token = jwksServer.createSignedToken(claims);
        assertThrows(JwtException.class, () -> jwtDecoder.decode(token));
    }

    @Test
    @DisplayName("T-AUTH-06: Missing 'exp' claim is rejected")
    void tAuth06_missingExpClaimRejected() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .audience(AUDIENCE)
            .subject(UUID.randomUUID().toString())
            .claim("role", "authenticated")
            .build(); // No expirationTime set

        String token = jwksServer.createSignedToken(claims);
        assertThrows(JwtException.class, () -> jwtDecoder.decode(token));
    }

    @Test
    @DisplayName("T-AUTH-07: Wrong audience is rejected; valid audience list accepted")
    void tAuth07_audienceValidation() throws Exception {
        // Wrong audience
        JWTClaimsSet wrongAud = createValidClaimsBuilder()
            .audience("untrusted-app")
            .build();
        String tokenWrong = jwksServer.createSignedToken(wrongAud);
        assertThrows(JwtException.class, () -> jwtDecoder.decode(tokenWrong));

        // Audience list containing 'authenticated'
        JWTClaimsSet multiAud = createValidClaimsBuilder()
            .audience(List.of("client-app", "authenticated"))
            .build();
        String tokenValid = jwksServer.createSignedToken(multiAud);
        Jwt decoded = jwtDecoder.decode(tokenValid);
        assertNotNull(decoded);
    }

    @Test
    @DisplayName("T-AUTH-08: Non-UUID sub, empty sub, is_anonymous=true, and wrong role are rejected")
    void tAuth08_identityClaimsValidated() throws Exception {
        // Non-UUID subject
        JWTClaimsSet nonUuid = createValidClaimsBuilder()
            .subject("admin-user-not-uuid")
            .build();
        assertThrows(JwtException.class, () -> jwtDecoder.decode(jwksServer.createSignedToken(nonUuid)));

        // Empty subject
        JWTClaimsSet emptySub = createValidClaimsBuilder()
            .subject("")
            .build();
        assertThrows(JwtException.class, () -> jwtDecoder.decode(jwksServer.createSignedToken(emptySub)));

        // Anonymous session
        JWTClaimsSet anon = createValidClaimsBuilder()
            .claim("is_anonymous", true)
            .build();
        assertThrows(JwtException.class, () -> jwtDecoder.decode(jwksServer.createSignedToken(anon)));

        // Wrong role
        JWTClaimsSet badRole = createValidClaimsBuilder()
            .claim("role", "anon")
            .build();
        assertThrows(JwtException.class, () -> jwtDecoder.decode(jwksServer.createSignedToken(badRole)));
    }

    @Test
    @DisplayName("T-AUTH-09: Unknown kid is rejected fail-closed")
    void tAuth09_unknownKidRejected() throws Exception {
        JWTClaimsSet claims = createValidClaimsBuilder().build();
        String token = jwksServer.createTokenWithUnknownKid(claims);

        assertThrows(JwtException.class, () -> jwtDecoder.decode(token));
    }

    @Test
    @DisplayName("T-AUTH-10: Symmetric algorithm (HS256) and 'none' algorithm are rejected")
    void tAuth10_algorithmConfusionRejected() throws Exception {
        JWTClaimsSet claims = createValidClaimsBuilder().build();

        // HS256 HMAC
        String hmacToken = jwksServer.createHmacToken(claims, "super-secret-hmac-key-must-be-long-enough");
        assertThrows(JwtException.class, () -> jwtDecoder.decode(hmacToken));

        // none algorithm
        String noneToken = jwksServer.createUnsignedNoneToken(claims);
        assertThrows(JwtException.class, () -> jwtDecoder.decode(noneToken));
    }

    @Test
    @DisplayName("T-AUTH-11: Valid ES256 token is accepted")
    void tAuth11_validTokenAccepted() throws Exception {
        String expectedSub = UUID.randomUUID().toString();
        JWTClaimsSet claims = createValidClaimsBuilder()
            .subject(expectedSub)
            .build();

        String token = jwksServer.createSignedToken(claims);
        Jwt jwt = jwtDecoder.decode(token);

        assertNotNull(jwt);
        assertEquals(expectedSub, jwt.getSubject());
        assertEquals(ISSUER, jwt.getIssuer().toString());
    }

    @Test
    @DisplayName("T-AUTH-12: Key rotation - new key in JWKS is accepted")
    void tAuth12_keyRotationSupported() throws Exception {
        // Rotate key on the JWKS server
        jwksServer.rotateKey();

        JWTClaimsSet claims = createValidClaimsBuilder().build();
        String token = jwksServer.createSignedToken(claims);

        Jwt jwt = jwtDecoder.decode(token);
        assertNotNull(jwt);
    }
}
