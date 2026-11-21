package com.moga.khronosdemo.notifications;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

/**
 * The two pieces of Web Push that need cryptography, built on the JDK alone:
 * <ul>
 *   <li>the message encryption of RFC 8291 (aes128gcm, RFC 8188), so only the device can read it, and</li>
 *   <li>the VAPID header of RFC 8292, a JWT signed with ES256 that tells the push service who sends.</li>
 * </ul>
 * Keys travel as base64url: public keys as uncompressed P-256 points (65 bytes, 0x04 || x || y) and the
 * private key as its 32-byte scalar, the format browsers and web-push generate-vapid-keys use.
 */
public final class WebPushCrypto {

    // One record holds the whole message: push services accept up to 4096 bytes of body
    private static final int RECORD_SIZE = 4096;
    private static final int MAX_PLAINTEXT = 3993;
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DECODER = Base64.getUrlDecoder();
    private static final ECParameterSpec P256 = p256();
    private static final SecureRandom RANDOM = new SecureRandom();

    private WebPushCrypto() {
    }

    /** Encrypts with a fresh key pair and salt, as every message must. */
    public static byte[] encrypt(byte[] plaintext, String userAgentPublicKey, String authSecret) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(P256, RANDOM);
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            return encrypt(plaintext, publicKey(userAgentPublicKey), decode(authSecret), generator.generateKeyPair(), salt);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not encrypt the push message", ex);
        }
    }

    // RFC 8291 section 3.4, with the ephemeral key pair and salt given so the RFC's example can be checked
    static byte[] encrypt(byte[] plaintext, ECPublicKey userAgentKey, byte[] authSecret, KeyPair ephemeral, byte[] salt)
            throws GeneralSecurityException {
        if (plaintext.length > MAX_PLAINTEXT) {
            throw new IllegalArgumentException("Push message too long: " + plaintext.length + " bytes");
        }
        byte[] uaPublic = encode(userAgentKey);
        byte[] asPublic = encode((ECPublicKey) ephemeral.getPublic());

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(ephemeral.getPrivate());
        agreement.doPhase(userAgentKey, true);
        byte[] ecdhSecret = agreement.generateSecret();

        byte[] prkKey = hmac(authSecret, ecdhSecret);
        byte[] ikm = hmac(prkKey, concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic, new byte[]{1}));
        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, "Content-Encoding: aes128gcm\0\1".getBytes(StandardCharsets.US_ASCII)), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, "Content-Encoding: nonce\0\1".getBytes(StandardCharsets.US_ASCII)), 12);

        // A single, last record: the content followed by the 0x02 delimiter and no padding
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] ciphertext = cipher.doFinal(concat(plaintext, new byte[]{2}));

        ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length);
        header.put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic);
        return concat(header.array(), ciphertext);
    }

    /** The Authorization header value for a message to this endpoint, valid for 12 hours. */
    public static String vapidAuthorization(URI endpoint, String subject, String publicKey, String privateKey, Instant now) {
        String audience = endpoint.getScheme() + "://" + endpoint.getRawAuthority();
        String header = B64.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = B64.encodeToString("{\"aud\":\"%s\",\"exp\":%d,\"sub\":\"%s\"}"
                .formatted(audience, now.plusSeconds(12 * 3600).getEpochSecond(), subject)
                .getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + claims;
        try {
            return "vapid t=" + signingInput + "." + B64.encodeToString(sign(signingInput, privateKey(privateKey)))
                    + ", k=" + publicKey;
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("The VAPID private key is not valid", ex);
        }
    }

    /** Signs and verifies with the key pair, so a public key that is not the private key's fails at startup. */
    public static void checkKeyPair(String publicKey, String privateKey) {
        try {
            byte[] probe = "khronos".getBytes(StandardCharsets.US_ASCII);
            byte[] signature = sign("khronos", privateKey(privateKey));
            Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
            verifier.initVerify(publicKey(publicKey));
            verifier.update(probe);
            if (!verifier.verify(signature)) {
                throw new IllegalStateException("The VAPID public key does not match the private key");
            }
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("The VAPID keys are not valid P-256 keys in base64url: " + ex.getMessage(), ex);
        }
    }

    /** Reads a browser's p256dh key; rejects anything that is not a point on P-256. */
    public static ECPublicKey publicKey(String base64url) throws GeneralSecurityException {
        byte[] bytes = decode(base64url);
        if (bytes.length != 65 || bytes[0] != 4) {
            throw new GeneralSecurityException("Not an uncompressed P-256 point");
        }
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(bytes, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(bytes, 33, 65)));
        ECPublicKey key = (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, P256));
        // Key agreement checks that the point is on the curve
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(P256, RANDOM);
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(generator.generateKeyPair().getPrivate());
        agreement.doPhase(key, true);
        return key;
    }

    static ECPrivateKey privateKey(String base64url) throws GeneralSecurityException {
        byte[] bytes = decode(base64url);
        if (bytes.length != 32) {
            throw new GeneralSecurityException("Not a 32-byte P-256 private key");
        }
        return (ECPrivateKey) KeyFactory.getInstance("EC")
                .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, bytes), P256));
    }

    static byte[] encode(ECPublicKey key) {
        return concat(new byte[]{4}, unsigned32(key.getW().getAffineX()), unsigned32(key.getW().getAffineY()));
    }

    public static byte[] decode(String base64url) {
        return B64_DECODER.decode(base64url.replace('+', '-').replace('/', '_').replace("=", ""));
    }

    static ECParameterSpec p256Spec() {
        return P256;
    }

    private static byte[] sign(String input, ECPrivateKey key) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
            signature.initSign(key);
            signature.update(input.getBytes(StandardCharsets.US_ASCII));
            return signature.sign();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not sign the VAPID token", ex);
        }
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    // A coordinate as exactly 32 bytes: BigInteger may add a sign byte or drop leading zeros
    private static byte[] unsigned32(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] out = new byte[32];
        int length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - length, out, 32 - length, length);
        return out;
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("This JVM has no P-256 curve", ex);
        }
    }
}
