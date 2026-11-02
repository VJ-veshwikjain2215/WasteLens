package com.wastelens.security.verifier;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.jca.JCAContext;
import com.nimbusds.jose.util.Base64URL;

import java.security.PublicKey;
import java.security.Signature;
import java.util.Set;

/**
 * JWSVerifier implementation using Java's built-in SunEC Ed25519 Signature engine (RFC 8032 / RFC 8410).
 */
public class JavaEd25519Verifier implements JWSVerifier {

    private final PublicKey publicKey;
    private final JCAContext jcaContext = new JCAContext();

    public JavaEd25519Verifier(PublicKey publicKey) {
        if (publicKey == null) {
            throw new IllegalArgumentException("The public key must not be null");
        }
        this.publicKey = publicKey;
    }

    @Override
    public boolean verify(JWSHeader header, byte[] signingInput, Base64URL signature) throws JOSEException {
        if (!JWSAlgorithm.EdDSA.equals(header.getAlgorithm())) {
            return false;
        }
        try {
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(publicKey);
            sig.update(signingInput);
            return sig.verify(signature.decode());
        } catch (Exception e) {
            throw new JOSEException("Ed25519 signature verification failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Set<JWSAlgorithm> supportedJWSAlgorithms() {
        return Set.of(JWSAlgorithm.EdDSA);
    }

    @Override
    public JCAContext getJCAContext() {
        return jcaContext;
    }
}
