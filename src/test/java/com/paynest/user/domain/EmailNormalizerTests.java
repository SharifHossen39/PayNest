package com.paynest.user.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class EmailNormalizerTests {

    private static final Instant CREATED_AT = Instant.parse("2026-10-07T08:30:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(CREATED_AT, ZoneOffset.UTC);

    @Test
    void normalizesEmailWithLocaleIndependentLowercaseAndOuterWhitespaceRemoval() {
        assertThat(EmailNormalizer.normalize("\u2003\u00A0PERSON@EXAMPLE.COM\u00A0\u2003"))
                .isEqualTo("person@example.com");
    }

    @Test
    void createsUserWithTrimmedIdentityAndDeterministicUtcTime() {
        Clock nanosecondClock = Clock.fixed(
                Instant.parse("2026-10-07T08:30:00.123456789Z"), ZoneOffset.UTC);
        User user = User.create(
                "\u2003Ada Lovelace\u00A0", " ADA@EXAMPLE.COM ", "{hash}opaque", nanosecondClock);

        assertThat(user.getName()).isEqualTo("Ada Lovelace");
        assertThat(user.getEmail()).isEqualTo("ada@example.com");
        assertThat(user.getPasswordHash()).isEqualTo("{hash}opaque");
        assertThat(user.getCreatedAt()).isEqualTo(Instant.parse("2026-10-07T08:30:00.123456Z"));
        assertThat(user.getId()).isNull();
    }

    @Test
    void acceptsCodePointAndPersistedLengthBoundaries() {
        String twoCodePointName = "\uD83D\uDE00A";
        String hundredCodePointName = "\uD83D\uDE00".repeat(100);
        String emailAtLimit = "\uD83D\uDE00".repeat(242) + "@example.com";
        String hashAtLimit = "h".repeat(255);

        User minimum = User.create(twoCodePointName, "minimum@example.com", hashAtLimit, FIXED_CLOCK);
        User maximum = User.create(hundredCodePointName, emailAtLimit, hashAtLimit, FIXED_CLOCK);

        assertThat(minimum.getName().codePointCount(0, minimum.getName().length())).isEqualTo(2);
        assertThat(maximum.getName().codePointCount(0, maximum.getName().length())).isEqualTo(100);
        assertThat(maximum.getEmail().codePointCount(0, maximum.getEmail().length())).isEqualTo(254);
        assertThat(maximum.getPasswordHash()).hasSize(255);
    }

    @Test
    void rejectsNullBlankAndOutOfBoundPersistedValues() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> User.create(null, "a@example.com", "hash", FIXED_CLOCK));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> User.create(" ", "a@example.com", "hash", FIXED_CLOCK));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> User.create("\u2003\u00A0", "a@example.com", "hash", FIXED_CLOCK));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> User.create("A", "a@example.com", "hash", FIXED_CLOCK));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> User.create("\uD83D\uDE00".repeat(101), "a@example.com", "hash", FIXED_CLOCK));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> User.create("Valid Name", " ", "hash", FIXED_CLOCK));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> User.create("Valid Name", "\uD83D\uDE00".repeat(255), "hash", FIXED_CLOCK));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> User.create("Valid Name", "a@example.com", " ", FIXED_CLOCK));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> User.create("Valid Name", "a@example.com", "h".repeat(256), FIXED_CLOCK));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> User.create("Valid Name", "a@example.com", "hash", null));
    }
}
