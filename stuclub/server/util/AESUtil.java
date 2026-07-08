package server.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public class AESUtil {
    private static final String AES_KEY = "ChatRoomNSFWKey2024!@#";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    public static SecretKeySpec generateKey() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = digest.digest(AES_KEY.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, "AES");
    }

    public static byte[] decrypt(byte[] encryptedData, byte[] iv) throws Exception {
        SecretKeySpec key = generateKey();
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        return cipher.doFinal(encryptedData);
    }

    public static String decryptBase64(String encryptedBase64, String ivBase64) throws Exception {
        byte[] encryptedData = Base64.getDecoder().decode(encryptedBase64);
        byte[] iv = Base64.getDecoder().decode(ivBase64);
        byte[] decryptedData = decrypt(encryptedData, iv);
        return new String(decryptedData, StandardCharsets.UTF_8);
    }

    public static byte[] decryptBase64ToBytes(String encryptedBase64, String ivBase64) throws Exception {
        byte[] encryptedData = Base64.getDecoder().decode(encryptedBase64);
        byte[] iv = Base64.getDecoder().decode(ivBase64);
        return decrypt(encryptedData, iv);
    }
}
