package com.wastelens.security.jwks;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class TestJwksServer implements AutoCloseable {

    private final HttpServer server;
    private final int port;
    private final List<JWK> keys = Collections.synchronizedList(new ArrayList<>());
    private ECKey currentEcKey;

    public TestJwksServer() throws Exception {
        this.server = HttpServer.create(new InetSocketAddress(0), 0);
        this.port = server.getAddress().getPort();

        // Initialize with first ES256 key
        rotateKey();

        server.createContext("/.well-known/jwks.json", exchange -> {
            JWKSet set;
            synchronized (keys) {
                set = new JWKSet(new ArrayList<>(keys));
            }
            byte[] response = set.toPublicJWKSet().toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        server.start();
    }

    public int getPort() {
        return port;
    }

    public String getJwksUri() {
        return "http://localhost:" + port + "/.well-known/jwks.json";
    }

    public synchronized void rotateKey() throws Exception {
        String kid = UUID.randomUUID().toString();
        this.currentEcKey = new ECKeyGenerator(Curve.P_256)
            .keyUse(KeyUse.SIGNATURE)
            .keyID(kid)
            .generate();
        this.keys.add(currentEcKey);
    }

    public String createSignedToken(JWTClaimsSet claims) throws Exception {
        JWSSigner signer = new ECDSASigner(currentEcKey);
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
            .keyID(currentEcKey.getKeyID())
            .build();
        SignedJWT signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(signer);
        return signedJWT.serialize();
    }

    public String createTamperedToken(JWTClaimsSet claims) throws Exception {
        String token = createSignedToken(claims);
        String[] parts = token.split("\\.");
        // Tamper with the signature bytes
        return parts[0] + "." + parts[1] + "." + parts[2].substring(0, parts[2].length() - 4) + "AAAA";
    }

    public String createHmacToken(JWTClaimsSet claims, String secret) throws Exception {
        JWSSigner signer = new MACSigner(secret.getBytes(StandardCharsets.UTF_8));
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256)
            .keyID("hmac-key")
            .build();
        SignedJWT signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(signer);
        return signedJWT.serialize();
    }

    public String createUnsignedNoneToken(JWTClaimsSet claims) {
        String header = "{\"alg\":\"none\"}";
        String payload = claims.toString();
        String b64Header = Base64.getUrlEncoder().withoutPadding().encodeToString(header.getBytes(StandardCharsets.UTF_8));
        String b64Payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return b64Header + "." + b64Payload + ".";
    }

    public String createTokenWithUnknownKid(JWTClaimsSet claims) throws Exception {
        ECKey orphanKey = new ECKeyGenerator(Curve.P_256)
            .keyID("unknown-orphan-kid")
            .generate();
        JWSSigner signer = new ECDSASigner(orphanKey);
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
            .keyID(orphanKey.getKeyID())
            .build();
        SignedJWT signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(signer);
        return signedJWT.serialize();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
