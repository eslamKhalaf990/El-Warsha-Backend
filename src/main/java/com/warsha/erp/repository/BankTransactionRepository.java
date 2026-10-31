package com.warsha.erp.repository;

import com.warsha.erp.entities.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    List<BankTransaction> findByBankAccountIdOrderByCreatedAtDesc(Long bankAccountId);

    List<BankTransaction> findByReferenceTypeAndReferenceId(String referenceType, Long referenceId);

    @Query("SELECT SUM(CASE WHEN t.transactionType = 'Deposit' THEN t.amount ELSE -t.amount END) " +
           "FROM BankTransaction t WHERE t.bankAccount.id = :accountId")
    BigDecimal getBalanceByAccount(@Param("accountId") Long accountId);

    @Query("""
            SELECT SUM(CASE WHEN t.transactionType = 'Deposit' THEN t.amount ELSE 0 END),
            SUM(CASE WHEN t.transactionType = 'Withdrawal' THEN t.amount ELSE 0 END),
            SUM(CASE WHEN t.transactionType = 'Deposit' THEN t.amount WHEN t.transactionType = 'Withdrawal' THEN -t.amount END)
        FROM BankTransaction t
    """)
    List<Object[]> getTotalSummary();

}
