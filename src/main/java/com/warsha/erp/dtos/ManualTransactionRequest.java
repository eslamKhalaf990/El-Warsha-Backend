package com.warsha.erp.dtos;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Body of POST /api/bank/transaction: a deposit or withdrawal entered by staff. */
@Getter
@Setter
public class ManualTransactionRequest {

    @NotNull(message = "Choose the bank account.")
    private Long bankAccountId;

    private Long categoryId;

    @NotBlank(message = "Choose deposit or withdrawal.")
    @Pattern(regexp = "Deposit|Withdrawal", message = "Choose deposit or withdrawal.")
    private String transactionType;

    @NotNull(message = "Enter the amount.")
    @Positive(message = "Amount must be more than 0.")
    @Digits(integer = 12, fraction = 2, message = "Amount can have at most 2 decimal places.")
    private BigDecimal amount;

    @Size(max = 255, message = "Description can be at most 255 characters.")
    private String description;
}
