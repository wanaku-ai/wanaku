package ai.wanaku.core.config.provider.file;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Simple utility class for encrypting and decrypting data using AES-256.
 */
public final class EncryptionHelper {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int IV_LENGTH = 16;

    private EncryptionHelper() {}

    /**
     * Encrypts data using AES-256-CBC.
     *
     * @param data     the data to encrypt
     * @param password the encryption password
     * @param salt     the salt for key derivation
     * @return encrypted data with IV prepended
     */
    public static byte[] encrypt(byte[] data, String password, String salt) throws Exception {
        byte[] key = deriveKey(password, salt);
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(data);

            // Prepend IV to ciphertext
            byte[] result = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, result, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, result, IV_LENGTH, encrypted.length);
            return result;
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    /**
     * Decrypts data that was encrypted with {@link #encrypt}.
     *
     * @param data     the encrypted data (IV + ciphertext)
     * @param password the encryption password
     * @param salt     the salt for key derivation
     * @return decrypted data
     * @throws IllegalArgumentException if data is null or too short
     */
    public static byte[] decrypt(byte[] data, String password, String salt) throws Exception {
        if (data == null || data.length < IV_LENGTH) {
            throw new IllegalArgumentException("Invalid encrypted data: too short");
        }

        byte[] key = deriveKey(password, salt);
        try {
            byte[] iv = Arrays.copyOfRange(data, 0, IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(data, IV_LENGTH, data.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return cipher.doFinal(ciphertext);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private static byte[] deriveKey(String password, String salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes(StandardCharsets.UTF_8), 65536, 256);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return factory.generateSecret(spec).getEncoded();
    }
}
