package org.kidscafe.kindy.util;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;

@Component
public class EncryptUtil {
    @Value("${kindy.encrypt.salt}")
    private String PEPPER;

    @Value("${kindy.encrypt.key}")
    private String KEY;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Refuses to start on a key this class cannot use.
     *
     * <p>Without this the first symptom of a mistyped AES_KEY is an InvalidKeyException — or, for a
     * blank one, "Empty key" — thrown out of a parent looking at their own address, long after the
     * deployment was called a success. The key is checked at the only moment it can still be fixed
     * cheaply.
     *
     * <p>16, 24 and 32 bytes are all accepted even though the methods below are named for AES-128.
     * The name records the intent, not a limit: SecretKeySpec and AES/CBC/PKCS5Padding take any of
     * the three unchanged, and a deployment already running a 256-bit key would be shut down by a
     * stricter check for no gain. What matters is that it is one of the three and not, say, the
     * fifteen characters somebody's editor left behind.
     *
     * <p>The pepper is only required to be present. Its length is not prescribed — it is fed to a
     * SHA-256 update rather than used as a key — but a blank one silently weakens every password
     * hash, and a null one throws on the login path.
     */
    @PostConstruct
    void validate() {
        if (PEPPER == null || PEPPER.isBlank())
            throw new IllegalStateException("SALT (kindy.encrypt.salt) must not be blank");

        int length = KEY == null ? 0 : KEY.getBytes(StandardCharsets.UTF_8).length;
        if (length != 16 && length != 24 && length != 32)
            throw new IllegalStateException(
                    "AES_KEY (kindy.encrypt.key) must be 16, 24 or 32 bytes of UTF-8, but is " + length);
    }

    public byte[] getSecureSalt() {
        byte[] ivBytes = new byte[16];
        secureRandom.nextBytes(ivBytes);
        return ivBytes;
    }

    public byte[] encHashSHA256(String str) {
        return encHashSHA256(str, (byte[]) null);
    }

    public byte[] encHashSHA256(String str, String salt) { return encHashSHA256(str, salt.getBytes(StandardCharsets.UTF_8)); }

    public byte[] encHashSHA256(String str, byte[] salt) {
        if (str == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(PEPPER.getBytes(StandardCharsets.UTF_8));
            digest.update(str.getBytes(StandardCharsets.UTF_8));
            if (salt != null) digest.update(salt);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException();
        }
    }

    public byte[] encAES128CBC(String str)
            throws NoSuchAlgorithmException, NoSuchPaddingException,
            InvalidKeyException, InvalidAlgorithmParameterException,
            IllegalBlockSizeException, BadPaddingException {
        if (str == null) return null;
        return encAES128CBC(str.getBytes(StandardCharsets.UTF_8));
    }

    public byte[] encAES128CBC(byte[] textBytes)
            throws NoSuchAlgorithmException, NoSuchPaddingException,
            InvalidKeyException, InvalidAlgorithmParameterException,
            IllegalBlockSizeException, BadPaddingException {

        if (textBytes == null) return null;

        byte[] ivBytes = new byte[16];
        secureRandom.nextBytes(ivBytes);

        SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

        byte[] result = cipher.doFinal(textBytes);

        ByteBuffer buffer = ByteBuffer.allocate(16 + result.length);
        buffer.put(ivBytes);
        buffer.put(result);

        return buffer.array();
    }

    public String decAES128CBC(byte[] encryptedBytes)
            throws NoSuchAlgorithmException, NoSuchPaddingException,
            InvalidKeyException, InvalidAlgorithmParameterException,
            IllegalBlockSizeException, BadPaddingException {

        if (encryptedBytes == null || encryptedBytes.length < 16) return null;

        ByteBuffer buffer = ByteBuffer.wrap(encryptedBytes);
        byte[] ivBytes = new byte[16];
        buffer.get(ivBytes);
        byte[] contents = new byte[buffer.remaining()];
        buffer.get(contents);

        SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

        byte[] decrypted = cipher.doFinal(contents);

        return new String(decrypted, StandardCharsets.UTF_8);
    }
}
