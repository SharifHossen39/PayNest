package com.paynest.wallet.domain;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;

import com.paynest.user.domain.User;

import jakarta.persistence.FetchType;
import jakarta.persistence.OneToOne;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class WalletTests {

    private static final Instant CREATED_AT = Instant.parse("2026-10-07T08:30:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(CREATED_AT, ZoneOffset.UTC);

    @Test
    void createsZeroBalanceUsdWalletWithDeterministicTime() {
        User user = user();
        Clock nanosecondClock = Clock.fixed(
                Instant.parse("2026-10-07T08:30:00.123456789Z"), ZoneOffset.UTC);

        Wallet wallet = Wallet.create(user, nanosecondClock);

        assertThat(wallet.getId()).isNull();
        assertThat(wallet.getUser()).isSameAs(user);
        assertThat(wallet.getBalance()).isEqualByComparingTo("0.00");
        assertThat(wallet.getBalance().scale()).isEqualTo(2);
        assertThat(wallet.getCurrency()).isEqualTo(Wallet.USD);
        assertThat(wallet.getCreatedAt()).isEqualTo(Instant.parse("2026-10-07T08:30:00.123456Z"));
        assertThatIllegalArgumentException().isThrownBy(() -> Wallet.create(null, FIXED_CLOCK));
        assertThatIllegalArgumentException().isThrownBy(() -> Wallet.create(user, null));
    }

    @Test
    void creditsAndDebitsWithExactScaleTwoArithmetic() {
        Wallet wallet = Wallet.create(user(), FIXED_CLOCK);

        wallet.credit(new BigDecimal("12.30"));
        wallet.credit(new BigDecimal("0.70"));
        wallet.debit(new BigDecimal("3.25"));

        assertThat(wallet.getBalance()).isEqualByComparingTo("9.75");
        assertThat(wallet.getBalance().scale()).isEqualTo(2);
    }

    @Test
    void rejectedOperationsNeverMutateTheBalance() {
        Wallet wallet = Wallet.create(user(), FIXED_CLOCK);
        wallet.credit(new BigDecimal("10.00"));

        assertThatIllegalArgumentException().isThrownBy(() -> wallet.credit(BigDecimal.ZERO));
        assertThat(wallet.getBalance()).isEqualByComparingTo("10.00");
        assertThatIllegalArgumentException().isThrownBy(() -> wallet.credit(new BigDecimal("0.001")));
        assertThat(wallet.getBalance()).isEqualByComparingTo("10.00");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> wallet.credit(new BigDecimal("99999999999999999.99")));
        assertThat(wallet.getBalance()).isEqualByComparingTo("10.00");
        assertThatIllegalStateException().isThrownBy(() -> wallet.debit(new BigDecimal("10.01")));
        assertThat(wallet.getBalance()).isEqualByComparingTo("10.00");
        assertThatIllegalArgumentException().isThrownBy(() -> wallet.debit(new BigDecimal("-1.00")));
        assertThat(wallet.getBalance()).isEqualByComparingTo("10.00");
        assertThatIllegalArgumentException().isThrownBy(
                () -> wallet.credit(new BigDecimal(BigInteger.ONE, Integer.MIN_VALUE)));
        assertThat(wallet.getBalance()).isEqualByComparingTo("10.00");
    }

    @Test
    void acceptsMaximumCreditAndExactBalanceDebitBoundaries() {
        Wallet wallet = Wallet.create(user(), FIXED_CLOCK);

        wallet.credit(new BigDecimal("99999999999999999.99"));
        assertThat(wallet.getBalance()).isEqualByComparingTo("99999999999999999.99");
        assertThat(wallet.getBalance().scale()).isEqualTo(2);

        wallet.debit(new BigDecimal("99999999999999999.99"));
        assertThat(wallet.getBalance()).isEqualByComparingTo("0.00");
        assertThat(wallet.getBalance().scale()).isEqualTo(2);
    }

    @Test
    void mappingIsRequiredLazyUnidirectionalAndHasNoCascade() throws NoSuchFieldException {
        Field userField = Wallet.class.getDeclaredField("user");
        OneToOne association = userField.getAnnotation(OneToOne.class);

        assertThat(association).isNotNull();
        assertThat(association.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(association.optional()).isFalse();
        assertThat(association.cascade()).isEmpty();
        assertThat(association.orphanRemoval()).isFalse();
        assertThat(Arrays.stream(User.class.getDeclaredFields()).map(Field::getName))
                .doesNotContain("wallet", "wallets");
    }

    private static User user() {
        return User.create("Ada Lovelace", "ada@example.com", "{hash}opaque", FIXED_CLOCK);
    }
}
