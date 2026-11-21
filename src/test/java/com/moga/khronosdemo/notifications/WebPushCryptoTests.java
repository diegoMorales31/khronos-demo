package com.moga.khronosdemo.notifications;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPrivateKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebPushCryptoTests {

    // RFC 8291, Appendix A
    private static final String RFC_PLAINTEXT = "When I grow up, I want to be a watermelon";
    private static final String RFC_AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String RFC_AS_PUBLIC = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String RFC_UA_PUBLIC = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String RFC_SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String RFC_AUTH = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String RFC_MESSAGE = "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";

    // A device's key pair and a VAPID pair, made with Node's crypto for these tests
    private static final String UA_PUBLIC = "BBQ8TGzcOPkE6Ue6b3Q_VQT7HcAPxQcj7lxz1YkcbySWOCYosmLLm5BtBfV0kQ-KvvxApXr2JFCbdygZ_RRO1-E";
    private static final String UA_PRIVATE = "dGoA-DU_Cm1M7_jPnrYXugJJzczgUIVM9MzaoLshKp0";
    private static final String AUTH = "1MFE0QgKg3vX4AJBXQjXYA";
    private static final String VAPID_PUBLIC = "BFfioXriDLXRtXOUjFkHaVKNk-2Z0qX_IvsJzFF8HoyfMH8jeyl1R_GuCEapxHukXU61-AZt_OOSEPg-i5257Gw";
    private static final String VAPID_PRIVATE = "4_mRhhgln0JUXxD5sUP54VO4cn9mf8pSe_r8NuZndNE";

    private static final Base64.Decoder B64 = Base64.getUrlDecoder();

    @Test
    void encryptsTheRfcExampleToTheRfcMessage() throws Exception {
        KeyPair ephemeral = new KeyPair(WebPushCrypto.publicKey(RFC_AS_PUBLIC), WebPushCrypto.privateKey(RFC_AS_PRIVATE));

        byte[] message = WebPushCrypto.encrypt(RFC_PLAINTEXT.getBytes(StandardCharsets.UTF_8),
                WebPushCrypto.publicKey(RFC_UA_PUBLIC), B64.decode(RFC_AUTH), ephemeral, B64.decode(RFC_SALT));

        assertArrayEquals(B64.decode(RFC_MESSAGE), message);
    }

    @Test
    void theDeviceCanReadWhatItIsSent() throws Exception {
        byte[] message = WebPushCrypto.encrypt("{\"title\":\"New shift\"}".getBytes(StandardCharsets.UTF_8), UA_PUBLIC, AUTH);

        assertEquals("{\"title\":\"New shift\"}", decrypt(message, UA_PUBLIC, UA_PRIVATE, AUTH));
    }

    @Test
    void everyMessageUsesItsOwnKeyAndSalt() {
        byte[] first = WebPushCrypto.encrypt(new byte[]{1}, UA_PUBLIC, AUTH);
        byte[] second = WebPushCrypto.encrypt(new byte[]{1}, UA_PUBLIC, AUTH);

        assertTrue(!Arrays.equals(first, second));
    }

    @Test
    void theVapidTokenIsSignedForThePushServicesOrigin() throws Exception {
        Instant now = Instant.parse("2030-05-10T12:00:00Z");
        String header = WebPushCrypto.vapidAuthorization(URI.create("https://fcm.googleapis.com/fcm/send/abc"),
                "mailto:ops@example.test", VAPID_PUBLIC, VAPID_PRIVATE, now);

        assertTrue(header.startsWith("vapid t="));
        assertTrue(header.endsWith(", k=" + VAPID_PUBLIC));
        String[] jwt = header.substring("vapid t=".length(), header.indexOf(',')).split("\\.");
        assertEquals("{\"aud\":\"https://fcm.googleapis.com\",\"exp\":" + now.plusSeconds(12 * 3600).getEpochSecond()
                + ",\"sub\":\"mailto:ops@example.test\"}", new String(B64.decode(jwt[1]), StandardCharsets.UTF_8));

        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(WebPushCrypto.publicKey(VAPID_PUBLIC));
        verifier.update((jwt[0] + "." + jwt[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(verifier.verify(B64.decode(jwt[2])));
    }

    @Test
    void aPublicKeyThatIsNotThePrivateKeysIsRefused() {
        WebPushCrypto.checkKeyPair(VAPID_PUBLIC, VAPID_PRIVATE);
        assertThrows(IllegalStateException.class, () -> WebPushCrypto.checkKeyPair(UA_PUBLIC, VAPID_PRIVATE));
        assertThrows(IllegalStateException.class, () -> WebPushCrypto.checkKeyPair("not-a-key", VAPID_PRIVATE));
    }

    @Test
    void aKeyThatIsNotAPointOnTheCurveIsRefused() {
        byte[] offCurve = new byte[65];
        offCurve[0] = 4;
        offCurve[64] = 1;
        assertThrows(Exception.class, () -> WebPushCrypto.publicKey(Base64.getUrlEncoder().withoutPadding().encodeToString(offCurve)));
    }

    // The receiving side of RFC 8291, as a browser does it
    private static String decrypt(byte[] message, String uaPublic, String uaPrivate, String auth) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(message);
        byte[] salt = new byte[16];
        buffer.get(salt);
        assertEquals(4096, buffer.getInt());
        byte[] asPublic = new byte[buffer.get()];
        buffer.get(asPublic);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        ECPrivateKey privateKey = (ECPrivateKey) KeyFactory.getInstance("EC")
                .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, B64.decode(uaPrivate)), WebPushCrypto.p256Spec()));
        ECPublicKey senderKey = WebPushCrypto.publicKey(Base64.getUrlEncoder().withoutPadding().encodeToString(asPublic));
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(privateKey);
        agreement.doPhase(senderKey, true);

        byte[] prkKey = hmac(B64.decode(auth), agreement.generateSecret());
        byte[] ikm = hmac(prkKey, join("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), B64.decode(uaPublic), asPublic, new byte[]{1}));
        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, "Content-Encoding: aes128gcm\0\1".getBytes(StandardCharsets.US_ASCII)), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, "Content-Encoding: nonce\0\1".getBytes(StandardCharsets.US_ASCII)), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);
        assertEquals(2, padded[padded.length - 1]);
        return new String(padded, 0, padded.length - 1, StandardCharsets.UTF_8);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] join(byte[]... parts) {
        int length = Arrays.stream(parts).mapToInt(part -> part.length).sum();
        ByteBuffer out = ByteBuffer.allocate(length);
        for (byte[] part : parts) {
            out.put(part);
        }
        return out.array();
    }
}
