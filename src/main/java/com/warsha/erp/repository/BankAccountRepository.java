package com.warsha.erp.repository;

import com.warsha.erp.dtos.BankAccountDTO;
import com.warsha.erp.dtos.TransactionCategoryDTO;
import com.warsha.erp.entities.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

    @Query("SELECT new com.warsha.erp.dtos.BankAccountDTO(b.id, b.name, b.accountType, b.currentBalance, b.createdAt, b.isOwnerSafe) " +
            "FROM BankAccount b WHERE b.isOwnerSafe = false")
    List<BankAccountDTO> findAvailableAccounts();

    @Query("SELECT new com.warsha.erp.dtos.BankAccountDTO(b.id, b.name, b.accountType, b.currentBalance, b.createdAt, b.isOwnerSafe) " +
            "FROM BankAccount b")
    List<BankAccountDTO> findAllAccounts();

    Optional<BankAccount> findFirstByIsOwnerSafeTrue();

    @Query("SELECT new com.warsha.erp.dtos.TransactionCategoryDTO(t.categoryID, t.categoryName, t.categoryType) FROM TransactionCategory t")
    List<TransactionCategoryDTO> findAllTransactionCategories();

    @Modifying
    @Query("UPDATE BankAccount b SET b.currentBalance = 0")
    void resetAllBalances();
}

