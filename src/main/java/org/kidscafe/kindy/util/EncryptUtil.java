package org.kidscafe.kindy.util;

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

    public byte[] encHashSHA256(String str) {
        return encHashSHA256(str, "");
    }

    public byte[] encHashSHA256(String str, String salt) {
        if (str == null) return null;
        String plainText = PEPPER + str + salt;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(plainText.getBytes());
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            return null;
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
