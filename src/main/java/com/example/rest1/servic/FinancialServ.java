
package com.example.rest1.servic;

import com.example.rest1.model.*;
import com.example.rest1.pip.*;
import com.example.rest1.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class FinancialServ {

    private final FinancialTransRepo transactions;
    private final MoneyRepo accounts;
    private final BudgetRepo categories;
    private final SavingsRepo goals;
    private final AccessServ access;

    public FinancialServ(
            FinancialTransRepo transactions,
            MoneyRepo accounts,
            BudgetRepo categories,
            SavingsRepo goals,
            AccessServ access
    ) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.categories = categories;
        this.goals = goals;
        this.access = access;
    }

    public List<FinancialTrans> all() {

        List<FinancialTrans> result = new ArrayList<>();

        for (FinancialTrans operation : transactions.findAll()) {

            if (access.isOwner(operation.account.owner.id)) {
                result.add(operation);
            }
        }

        return result;
    }

    public FinancialTrans get(Long id) {

        FinancialTrans operation = transactions.findById(id)
                .orElseThrow(() -> Check.missing("Transaction"));

        if (!access.isOwner(operation.account.owner.id)) {
            throw new AccessDeniedException("Access denied");
        }

        return operation;
    }

    public FinancialTrans save(Long id, FinancialReq request) {

        FinancialTrans operation;

        if (id == null) {

            operation = new FinancialTrans();
            operation.status = TransactionStatus.PENDING;
            operation.createdAt = LocalDateTime.now();

        } else {

            operation = get(id);

            Check.require(
                    operation.status == TransactionStatus.PENDING,
                    "Transaction is not pending"
            );
        }

        if (request.status() != null) {

            Check.require(
                    request.status() == TransactionStatus.PENDING,
                    "Use confirm operation"
            );
        }

        if (id == null || request.amount() != null) {

            Check.positive(request.amount());
            operation.amount = request.amount();
        }

        if (id == null || request.type() != null) {

            Check.require(
                    request.type() != null,
                    "Type required"
            );

            operation.type = request.type();
        }

        if (id == null || request.accountId() != null) {

            Check.require(
                    request.accountId() != null,
                    "Account required"
            );

            operation.account = accounts.findById(request.accountId())
                    .orElseThrow(() -> Check.missing("Account"));
        }

        if (!access.isOwner(operation.account.owner.id)) {
            throw new AccessDeniedException("Access denied");
        }

        if (operation.type == TransactionType.INCOME) {

            operation.category = null;
            operation.goal = null;
        }

        if (operation.type == TransactionType.EXPENSE) {
            operation.goal = null;
        }

        if (operation.type == TransactionType.CONTRIBUTION) {
            operation.category = null;
        }

        if (request.categoryId() != null) {

            operation.category = categories.findById(request.categoryId())
                    .orElseThrow(() -> Check.missing("Category"));
        }

        if (request.goalId() != null) {

            operation.goal = goals.findById(request.goalId())
                    .orElseThrow(() -> Check.missing("Goal"));
        }

        checkOperation(operation);

        return transactions.save(operation);
    }

    private void checkOperation(FinancialTrans operation) {

        if (!access.isOwner(operation.account.owner.id)) {
            throw new AccessDeniedException("Access denied");
        }

        if (operation.category != null) {

            if (!access.isOwner(operation.category.owner.id)) {
                throw new AccessDeniedException("Access denied");
            }

            Check.require(
                    operation.category.owner.id.equals(
                            operation.account.owner.id
                    ),
                    "Different owners"
            );
        }

        if (operation.goal != null) {

            if (!access.isOwner(operation.goal.owner.id)) {
                throw new AccessDeniedException("Access denied");
            }

            Check.require(
                    operation.goal.owner.id.equals(
                            operation.account.owner.id
                    ),
                    "Different owners"
            );

            Check.require(
                    operation.goal.account.id.equals(operation.account.id),
                    "Contribution must use goal account"
            );
        }

        if (operation.type == TransactionType.EXPENSE) {

            Check.require(
                    operation.category != null
                            && operation.goal == null,
                    "Expense requires category"
            );
        }

        if (operation.type == TransactionType.CONTRIBUTION) {

            Check.require(
                    operation.goal != null
                            && operation.category == null,
                    "Contribution requires goal"
            );
        }

        if (operation.type == TransactionType.INCOME) {

            Check.require(
                    operation.category == null
                            && operation.goal == null,
                    "Income cannot have category or goal"
            );
        }
    }

    public void delete(Long id) {

        FinancialTrans operation = get(id);

        Check.require(
                operation.status != TransactionStatus.CONFIRMED,
                "Cannot delete confirmed transaction"
        );

        transactions.delete(operation);
    }

    public FinancialTrans confirmTransaction(Long id) {

        FinancialTrans operation = get(id);

        Check.require(
                operation.status == TransactionStatus.PENDING,
                "Transaction is not pending"
        );

        checkOperation(operation);
        Check.positive(operation.amount);

        operation.createdAt = LocalDateTime.now();

        checkAmount(operation);

        operation.status = TransactionStatus.CONFIRMED;

        return transactions.save(operation);
    }

    public FinancialTrans payExpense(FinancialReq request) {

        Check.positive(request.amount());

        Check.require(
                request.accountId() != null,
                "Account required"
        );

        Check.require(
                request.categoryId() != null,
                "Category required"
        );

        MoneyAcc account = accounts.findById(request.accountId())
                .orElseThrow(() -> Check.missing("Account"));

        BudgetCategory category = categories.findById(request.categoryId())
                .orElseThrow(() -> Check.missing("Category"));

        FinancialTrans operation = new FinancialTrans();

        operation.amount = request.amount();
        operation.type = TransactionType.EXPENSE;
        operation.createdAt = LocalDateTime.now();
        operation.account = account;
        operation.category = category;

        checkOperation(operation);
        checkAmount(operation);

        operation.status = TransactionStatus.CONFIRMED;

        return transactions.save(operation);
    }

    private void checkAmount(FinancialTrans operation) {

        BigDecimal balance = operation.account.initialBalance;
        BigDecimal spent = BigDecimal.ZERO;
        BigDecimal saved = BigDecimal.ZERO;

        LocalDateTime now = operation.createdAt;

        for (FinancialTrans t : transactions.findAll()) {

            if (t.status != TransactionStatus.CONFIRMED) {
                continue;
            }

            if (t.account.id.equals(operation.account.id)) {

                if (t.type == TransactionType.INCOME) {
                    balance = balance.add(t.amount);
                } else {
                    balance = balance.subtract(t.amount);
                }
            }

            if (operation.category != null
                    && t.category != null
                    && t.type == TransactionType.EXPENSE
                    && t.category.id.equals(operation.category.id)
                    && t.createdAt.getYear() == now.getYear()
                    && t.createdAt.getMonthValue() == now.getMonthValue()) {

                spent = spent.add(t.amount);
            }

            if (operation.goal != null
                    && t.goal != null
                    && t.type == TransactionType.CONTRIBUTION
                    && t.goal.id.equals(operation.goal.id)) {

                saved = saved.add(t.amount);
            }
        }

        if (operation.type != TransactionType.INCOME) {

            Check.require(
                    balance.compareTo(operation.amount) >= 0,
                    "Not enough money"
            );
        }

        if (operation.type == TransactionType.EXPENSE) {

            Check.require(
                    spent.add(operation.amount).compareTo(
                            operation.category.monthlyExpenseLimit
                    ) <= 0,
                    "Monthly limit exceeded"
            );
        }

        if (operation.type == TransactionType.CONTRIBUTION) {

            Check.require(
                    operation.goal.status.equals("ACTIVE"),
                    "Goal is not active"
            );

            Check.require(
                    saved.add(operation.amount).compareTo(
                            operation.goal.targetAmount
                    ) <= 0,
                    "Goal amount exceeded"
            );
        }
    }
}
