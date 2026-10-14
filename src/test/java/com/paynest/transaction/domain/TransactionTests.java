package com.paynest.transaction.domain;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;

import com.paynest.user.domain.User;
import com.paynest.wallet.domain.Wallet;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TransactionTests {

    private static final Instant CREATED_AT = Instant.parse("2026-10-14T09:45:12.123456789Z");
    private static final Instant PERSISTED_CREATED_AT = Instant.parse("2026-10-14T09:45:12.123456Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(CREATED_AT, ZoneOffset.UTC);

    @Test
    void createsOnlyCompletedTransactionsWithExactAmountAndMicrosecondTime() {
        Wallet sender = wallet("Sender", "sender@example.com");
        Wallet receiver = wallet("Receiver", "receiver@example.com");

        Transaction transaction = Transaction.completed(
                sender, receiver, new BigDecimal("125.5"), FIXED_CLOCK);

        assertThat(transaction.getId()).isNull();
        assertThat(transaction.getSenderWallet()).isSameAs(sender);
        assertThat(transaction.getReceiverWallet()).isSameAs(receiver);
        assertThat(transaction.getAmount()).isEqualByComparingTo("125.50");
        assertThat(transaction.getAmount().scale()).isEqualTo(2);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(transaction.getCreatedAt()).isEqualTo(PERSISTED_CREATED_AT);
        assertThat(TransactionStatus.values()).containsExactly(TransactionStatus.COMPLETED);
    }

    @Test
    void rejectsMissingOrIdenticalParticipantsAndMissingClock() {
        Wallet sender = wallet("Sender", "sender@example.com");
        Wallet receiver = wallet("Receiver", "receiver@example.com");

        assertThatIllegalArgumentException().isThrownBy(
                () -> Transaction.completed(null, receiver, new BigDecimal("1.00"), FIXED_CLOCK));
        assertThatIllegalArgumentException().isThrownBy(
                () -> Transaction.completed(sender, null, new BigDecimal("1.00"), FIXED_CLOCK));
        assertThatIllegalArgumentException().isThrownBy(
                () -> Transaction.completed(sender, sender, new BigDecimal("1.00"), FIXED_CLOCK));
        assertThatIllegalArgumentException().isThrownBy(
                () -> Transaction.completed(sender, receiver, new BigDecimal("1.00"), null));
    }

    @Test
    void rejectsDifferentWalletObjectsThatRepresentTheSamePersistedWallet() throws Exception {
        Wallet sender = wallet("Sender", "sender@example.com");
        Wallet detachedCopy = wallet("Sender Copy", "sender-copy@example.com");
        setWalletId(sender, 42L);
        setWalletId(detachedCopy, 42L);

        assertThatIllegalArgumentException().isThrownBy(
                () -> Transaction.completed(sender, detachedCopy, new BigDecimal("1.00"), FIXED_CLOCK));
    }

    @Test
    void rejectsAmountsThatCannotBeStoredExactlyAsPositiveNumericNineteenTwo() {
        Wallet sender = wallet("Sender", "sender@example.com");
        Wallet receiver = wallet("Receiver", "receiver@example.com");

        assertThatIllegalArgumentException().isThrownBy(
                () -> Transaction.completed(sender, receiver, null, FIXED_CLOCK));
        assertThatIllegalArgumentException().isThrownBy(
                () -> Transaction.completed(sender, receiver, BigDecimal.ZERO, FIXED_CLOCK));
        assertThatIllegalArgumentException().isThrownBy(
                () -> Transaction.completed(sender, receiver, new BigDecimal("-0.01"), FIXED_CLOCK));
        assertThatIllegalArgumentException().isThrownBy(
                () -> Transaction.completed(sender, receiver, new BigDecimal("1.001"), FIXED_CLOCK));
        assertThatIllegalArgumentException().isThrownBy(
                () -> Transaction.completed(
                        sender, receiver, new BigDecimal("100000000000000000.00"), FIXED_CLOCK));
        assertThatIllegalArgumentException().isThrownBy(
                () -> Transaction.completed(
                        sender,
                        receiver,
                        new BigDecimal(BigInteger.ONE, Integer.MIN_VALUE),
                        FIXED_CLOCK));
    }

    @Test
    void acceptsTheExactMaximumStorableAmount() {
        Transaction transaction = Transaction.completed(
                wallet("Sender", "sender@example.com"),
                wallet("Receiver", "receiver@example.com"),
                new BigDecimal("99999999999999999.99"),
                FIXED_CLOCK);

        assertThat(transaction.getAmount()).isEqualByComparingTo("99999999999999999.99");
        assertThat(transaction.getAmount().scale()).isEqualTo(2);
    }

    @Test
    void mappingOwnsRequiredLazyAssociationsWithoutCascadeOrWalletBackCollections()
            throws NoSuchFieldException {
        assertWalletAssociation("senderWallet", "sender_wallet_id", "fk_transactions_sender_wallet");
        assertWalletAssociation("receiverWallet", "receiver_wallet_id", "fk_transactions_receiver_wallet");

        assertThat(Arrays.stream(Wallet.class.getDeclaredFields()).map(Field::getType))
                .doesNotContain(Transaction.class);
        assertThat(Arrays.stream(Wallet.class.getDeclaredFields()).map(Field::getName))
                .doesNotContain("transactions", "sentTransactions", "receivedTransactions");
        assertThat(Arrays.stream(Wallet.class.getDeclaredFields())
                .filter(TransactionTests::isJpaCollectionRelationship)
                .map(Field::getGenericType)
                .filter(ParameterizedType.class::isInstance)
                .map(ParameterizedType.class::cast)
                .flatMap(type -> Arrays.stream(type.getActualTypeArguments())))
                .doesNotContain(Transaction.class);
    }

    @Test
    void businessFieldsAreNonUpdatableAndExposeNoPublicMutationMethods() throws NoSuchFieldException {
        for (String fieldName : new String[]{
                "senderWallet", "receiverWallet", "amount", "status", "createdAt"
        }) {
            Field field = Transaction.class.getDeclaredField(fieldName);
            Column column = field.getAnnotation(Column.class);
            JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
            assertThat(column == null ? joinColumn.updatable() : column.updatable())
                    .as(fieldName)
                    .isFalse();
        }

        assertThat(Arrays.stream(Transaction.class.getMethods())
                .filter(method -> method.getDeclaringClass().equals(Transaction.class))
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName))
                .containsExactlyInAnyOrder(
                        "completed",
                        "getId",
                        "getSenderWallet",
                        "getReceiverWallet",
                        "getAmount",
                        "getStatus",
                        "getCreatedAt");
    }

    private static void assertWalletAssociation(
            String fieldName,
            String columnName,
            String foreignKeyName
    ) throws NoSuchFieldException {
        Field field = Transaction.class.getDeclaredField(fieldName);
        ManyToOne association = field.getAnnotation(ManyToOne.class);
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);

        assertThat(association).isNotNull();
        assertThat(association.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(association.optional()).isFalse();
        assertThat(association.cascade()).isEmpty();
        assertThat(joinColumn).isNotNull();
        assertThat(joinColumn.name()).isEqualTo(columnName);
        assertThat(joinColumn.nullable()).isFalse();
        assertThat(joinColumn.updatable()).isFalse();
        assertThat(joinColumn.foreignKey().name()).isEqualTo(foreignKeyName);
    }

    private static Wallet wallet(String name, String email) {
        User user = User.create(name, email, "{hash}opaque", FIXED_CLOCK);
        return Wallet.create(user, FIXED_CLOCK);
    }

    private static boolean isJpaCollectionRelationship(Field field) {
        return field.isAnnotationPresent(OneToMany.class)
                || field.isAnnotationPresent(ManyToMany.class);
    }

    private static void setWalletId(Wallet wallet, long id) throws Exception {
        Field idField = Wallet.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(wallet, id);
    }
}
