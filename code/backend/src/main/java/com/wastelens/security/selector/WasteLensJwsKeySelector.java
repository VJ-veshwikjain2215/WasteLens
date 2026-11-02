package com.wastelens.security.selector;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.AsymmetricJWK;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.SecurityContext;

import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Custom JWS key selector supporting both standard Asymmetric keys (ES256, RS256)
 * and RFC 8037 Octet Key Pairs (EdDSA / Ed25519 from Neon Auth) using Java's built-in
 * Ed25519 cryptographic provider.
 */
public class WasteLensJwsKeySelector implements JWSKeySelector<SecurityContext> {

    private static final byte[] ED25519_SPKI_PREFIX = new byte[]{
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };

    private final Set<JWSAlgorithm> expectedJwsAlgorithms;
    private final JWKSource<SecurityContext> jwkSource;

    public WasteLensJwsKeySelector(Set<JWSAlgorithm> expectedJwsAlgorithms, JWKSource<SecurityContext> jwkSource) {
        if (expectedJwsAlgorithms == null || expectedJwsAlgorithms.isEmpty()) {
            throw new IllegalArgumentException("The expected JWS algorithms must not be null or empty");
        }
        if (jwkSource == null) {
            throw new IllegalArgumentException("The JWK source must not be null");
        }
        this.expectedJwsAlgorithms = Collections.unmodifiableSet(expectedJwsAlgorithms);
        this.jwkSource = jwkSource;
    }

    @Override
    public List<? extends Key> selectJWSKeys(JWSHeader header, SecurityContext context) throws KeySourceException {
        if (!expectedJwsAlgorithms.contains(header.getAlgorithm())) {
            return Collections.emptyList();
        }

        JWKMatcher matcher = JWKMatcher.forJWSHeader(header);
        List<JWK> matches = jwkSource.get(new JWKSelector(matcher), context);
        List<Key> sanitizedKeyList = new ArrayList<>();

        for (JWK jwk : matches) {
            if (jwk instanceof OctetKeyPair okp && JWSAlgorithm.EdDSA.equals(header.getAlgorithm())) {
                try {
                    byte[] x = okp.getX().decode();
                    byte[] spki = new byte[ED25519_SPKI_PREFIX.length + x.length];
                    System.arraycopy(ED25519_SPKI_PREFIX, 0, spki, 0, ED25519_SPKI_PREFIX.length);
                    System.arraycopy(x, 0, spki, ED25519_SPKI_PREFIX.length, x.length);

                    KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");
                    PublicKey pubKey = keyFactory.generatePublic(new X509EncodedKeySpec(spki));
                    sanitizedKeyList.add(pubKey);
                } catch (Exception ignored) {
                }
            } else if (jwk instanceof AsymmetricJWK asym) {
                try {
                    PublicKey pk = asym.toPublicKey();
                    if (pk != null) {
                        sanitizedKeyList.add(pk);
                    }
                } catch (JOSEException ignored) {
                }
            }
        }

        return sanitizedKeyList;
    }
}
