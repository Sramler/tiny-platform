package com.tiny.platform.infrastructure.core.util;

import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class PemUtils {

    public static RSAPrivateKey readPrivateKey(String path) throws Exception {
        String pem = readPemFile(path);
        pem = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(pem);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) factory.generatePrivate(spec);
    }

    public static RSAPublicKey readPublicKey(String path) throws Exception {
        String pem = readPemFile(path);
        pem = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(pem);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) factory.generatePublic(spec);
    }

    private static String readPemFile(String path) throws Exception {
        if (path.startsWith("classpath:")) {
            String actualPath = path.substring("classpath:".length());
            try (InputStream is = new ClassPathResource(actualPath).getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            return java.nio.file.Files.readString(java.nio.file.Paths.get(path));
        }
    }
}