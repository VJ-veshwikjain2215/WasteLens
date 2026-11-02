package com.wastelens.security.verifier;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jose.jca.JCAContext;
import com.nimbusds.jose.proc.JWSVerifierFactory;

import java.security.Key;
import java.security.PublicKey;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Composite JWSVerifierFactory that delegates standard RSA/EC algorithms to Nimbus
 * DefaultJWSVerifierFactory while adding native Java EdDSA (Ed25519) verification support.
 */
public class WasteLensJwsVerifierFactory implements JWSVerifierFactory {

    private final DefaultJWSVerifierFactory defaultFactory = new DefaultJWSVerifierFactory();
    private final Set<JWSAlgorithm> supportedAlgs;

    public WasteLensJwsVerifierFactory() {
        Set<JWSAlgorithm> algs = new HashSet<>(defaultFactory.supportedJWSAlgorithms());
        algs.add(JWSAlgorithm.EdDSA);
        this.supportedAlgs = Collections.unmodifiableSet(algs);
    }

    @Override
    public JWSVerifier createJWSVerifier(JWSHeader header, Key key) throws JOSEException {
        if (JWSAlgorithm.EdDSA.equals(header.getAlgorithm()) && key instanceof PublicKey pk) {
            return new JavaEd25519Verifier(pk);
        }
        return defaultFactory.createJWSVerifier(header, key);
    }

    @Override
    public Set<JWSAlgorithm> supportedJWSAlgorithms() {
        return supportedAlgs;
    }

    @Override
    public JCAContext getJCAContext() {
        return defaultFactory.getJCAContext();
    }
}
