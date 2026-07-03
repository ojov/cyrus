package com.ojo.cyrus.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoUtilTest {

    // Deterministic, valid Base64 AES keys of each accepted length (16 / 24 / 32 bytes).
    private static final String KEY_128 = base64("0123456789abcdef");                 // 16 bytes
    private static final String KEY_192 = base64("0123456789abcdef01234567");         // 24 bytes
    private static final String KEY_256 = base64("0123456789abcdef0123456789abcdef"); // 32 bytes
    private static final String OTHER_KEY_256 = base64("fedcba9876543210fedcba9876543210");

    private static String base64(String raw) {
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static Stream<String> validKeys() {
        return Stream.of(KEY_128, KEY_192, KEY_256);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "nomba_client_secret",
            "a secret with spaces and symbols !@#$%^&*()",
            "unicode 🔐 密码 clé",
            "multi\nline\nsecret"
    })
    void encryptThenDecrypt_returnsOriginalPlaintext(String plaintext) {
        String ciphertext = CryptoUtil.encrypt(plaintext, KEY_256);

        assertThat(ciphertext).isNotEqualTo(plaintext);
        assertThat(CryptoUtil.decrypt(ciphertext, KEY_256)).isEqualTo(plaintext);
    }

    @ParameterizedTest
    @MethodSource("validKeys")
    void roundTrips_forEveryValidKeyLength(String key) {
        String plaintext = "sk_live_nomba_secret";

        assertThat(CryptoUtil.decrypt(CryptoUtil.encrypt(plaintext, key), key)).isEqualTo(plaintext);
    }

    @Test
    void decrypt_withDifferentKey_fails() {
        String ciphertext = CryptoUtil.encrypt("nomba_client_secret", KEY_256);

        // AES-GCM authentication tag check fails under the wrong key, wrapped by CryptoUtil.
        assertThatThrownBy(() -> CryptoUtil.decrypt(ciphertext, OTHER_KEY_256))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void encrypt_isNonDeterministic_dueToRandomIv() {
        String c1 = CryptoUtil.encrypt("nomba_client_secret", KEY_256);
        String c2 = CryptoUtil.encrypt("nomba_client_secret", KEY_256);

        assertThat(c1).isNotEqualTo(c2); // fresh 12-byte IV per call
        assertThat(CryptoUtil.decrypt(c1, KEY_256)).isEqualTo("nomba_client_secret");
        assertThat(CryptoUtil.decrypt(c2, KEY_256)).isEqualTo("nomba_client_secret");
    }

    @Test
    void key_isTrimmed_soSurroundingWhitespaceRoundTrips() {
        // Guards the trailing-newline env-var gotcha: aesKey() trims, so a key stored with a
        // stray newline still encrypts/decrypts consistently.
        String ciphertext = CryptoUtil.encrypt("nomba_client_secret", KEY_256 + "\n");

        assertThat(CryptoUtil.decrypt(ciphertext, "  " + KEY_256 + "  ")).isEqualTo("nomba_client_secret");
    }

    @Test
    void decrypt_withMalformedCiphertext_fails() {
        assertThatThrownBy(() -> CryptoUtil.decrypt("not-valid-base64-$$$", KEY_256))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void hmacSha256_isDeterministic_andVariesBySecret() {
        assertThat(CryptoUtil.hmacSha256("payload", "secret"))
                .isEqualTo(CryptoUtil.hmacSha256("payload", "secret"));
        assertThat(CryptoUtil.hmacSha256("payload", "secret"))
                .isNotEqualTo(CryptoUtil.hmacSha256("payload", "other-secret"));
    }

    @Test
    void sha256_isDeterministic_andVariesByInput() {
        assertThat(CryptoUtil.sha256("value")).isEqualTo(CryptoUtil.sha256("value"));
        assertThat(CryptoUtil.sha256("value")).isNotEqualTo(CryptoUtil.sha256("other"));
    }

    @Test
    void randomToken_isUrlSafe_andUnique() {
        String a = CryptoUtil.randomToken(32);
        String b = CryptoUtil.randomToken(32);

        assertThat(a).isNotEqualTo(b);
        assertThat(a).matches("[A-Za-z0-9_-]+"); // Base64 URL-safe, no padding
    }
}
