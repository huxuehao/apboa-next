package com.hxh.apboa.common.util;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 描述：RSA 密钥 PEM 编解码（身份断言签名密钥用，纯 JDK 无第三方依赖）
 *
 * @author vaulka
 */
public final class PemUtils {

    private static final String PRIVATE_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PRIVATE_FOOTER = "-----END PRIVATE KEY-----";
    private static final String PUBLIC_HEADER = "-----BEGIN PUBLIC KEY-----";
    private static final String PUBLIC_FOOTER = "-----END PUBLIC KEY-----";

    private PemUtils() {
    }

    /**
     * 生成 RSA 2048 密钥对
     */
    public static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA algorithm unavailable", e);
        }
    }

    /**
     * 私钥编码为 PKCS#8 PEM
     */
    public static String toPkcs8Pem(PrivateKey privateKey) {
        return wrapPem(privateKey.getEncoded(), PRIVATE_HEADER, PRIVATE_FOOTER);
    }

    /**
     * 公钥编码为 X.509 PEM
     */
    public static String toX509Pem(PublicKey publicKey) {
        return wrapPem(publicKey.getEncoded(), PUBLIC_HEADER, PUBLIC_FOOTER);
    }

    /**
     * 解析 PKCS#8 PEM 私钥
     */
    public static PrivateKey parsePkcs8PrivateKey(String pem) {
        try {
            byte[] der = Base64.getDecoder().decode(stripPem(pem));
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid PKCS#8 private key PEM", e);
        }
    }

    /**
     * 解析 X.509 PEM 公钥
     */
    public static PublicKey parseX509PublicKey(String pem) {
        try {
            byte[] der = Base64.getDecoder().decode(stripPem(pem));
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid X.509 public key PEM", e);
        }
    }

    private static String wrapPem(byte[] der, String header, String footer) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return header + "\n" + base64 + "\n" + footer;
    }

    private static String stripPem(String pem) {
        return pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
    }
}
