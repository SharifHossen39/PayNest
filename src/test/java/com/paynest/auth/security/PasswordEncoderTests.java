package com.paynest.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncoderTests {

    @Test
    void encodesAndMatchesTheCompleteSeventyTwoCodePointPasswordWithTheRequiredId() {
        PasswordEncoder encoder = new SecurityConfig().passwordEncoder();
        String password = "\uD83D\uDE00".repeat(64) + "Passw0rd";
        String changedLastCharacter = password.substring(0, password.length() - 1) + "X";

        String encoded = encoder.encode(password);

        assertThat(password.codePointCount(0, password.length())).isEqualTo(72);
        assertThat(encoded).startsWith("{pbkdf2@SpringSecurity_v5_8}");
        assertThat(encoder.matches(password, encoded)).isTrue();
        assertThat(encoder.matches(changedLastCharacter, encoded)).isFalse();
    }
}
