package com.paynest.transaction.persistence;

import java.util.Optional;

import com.paynest.transaction.domain.Transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query(
            value = """
                    select new com.paynest.transaction.persistence.TransactionHistoryRow(
                        history.id,
                        senderWallet.id,
                        senderUser.name,
                        senderUser.email,
                        receiverWallet.id,
                        receiverUser.name,
                        receiverUser.email,
                        history.amount,
                        history.status,
                        history.createdAt)
                    from Transaction history
                    join history.senderWallet senderWallet
                    join senderWallet.user senderUser
                    join history.receiverWallet receiverWallet
                    join receiverWallet.user receiverUser
                    where senderWallet.id = :walletId or receiverWallet.id = :walletId
                    order by history.createdAt desc, history.id desc
                    """,
            countQuery = """
                    select count(history.id)
                    from Transaction history
                    where history.senderWallet.id = :walletId
                        or history.receiverWallet.id = :walletId
                    """
    )
    Page<TransactionHistoryRow> findHistoryByParticipant(
            @Param("walletId") Long walletId,
            Pageable pageable
    );

    @Query("""
            select new com.paynest.transaction.persistence.TransactionDetailRow(
                detail.id,
                senderWallet.id,
                senderUser.name,
                senderUser.email,
                recipientUser.name,
                recipientUser.email,
                detail.amount,
                detail.status,
                detail.createdAt)
            from Transaction detail
            join detail.senderWallet senderWallet
            join senderWallet.user senderUser
            join detail.receiverWallet recipientWallet
            join recipientWallet.user recipientUser
            where detail.id = :transactionId
              and (senderWallet.id = :walletId or recipientWallet.id = :walletId)
            """)
    Optional<TransactionDetailRow> findDetailByIdAndParticipant(
            @Param("transactionId") Long transactionId,
            @Param("walletId") Long walletId
    );
}
