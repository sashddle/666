
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
public class SavingServ {

    private final SavingsRepo goals;
    private final UserRepo users;
    private final MoneyRepo accounts;
    private final FinancialTransRepo transactions;
    private final AccessServ access;

    public SavingServ(
            SavingsRepo goals,
            UserRepo users,
            MoneyRepo accounts,
            FinancialTransRepo transactions,
            AccessServ access
    ) {
        this.goals = goals;
        this.users = users;
        this.accounts = accounts;
        this.transactions = transactions;
        this.access = access;
    }

    public List<SavingsGoal> all() {

        List<SavingsGoal> result = new ArrayList<>();

        for (SavingsGoal goal : goals.findAll()) {
            if (access.isOwner(goal.owner.id)) {
                result.add(goal);
            }
        }

        return result;
    }

    public SavingsGoal get(Long id) {

        SavingsGoal goal = goals.findById(id)
                .orElseThrow(() -> Check.missing("Goal"));

        if (!access.isOwner(goal.owner.id)) {
            throw new AccessDeniedException("Access denied");
        }

        return goal;
    }

    public SavingsGoal save(Long id, SavingReq request) {

        SavingsGoal goal;

        if (id == null) {
            goal = new SavingsGoal();
        } else {
            goal = get(id);
        }

        if (id == null || request.name() != null) {
            Check.text(request.name());
            goal.name = request.name();
        }

        if (id == null) {

            Check.require(
                    request.status() == null
                            || request.status().equals("ACTIVE"),
                    "Invalid goal status"
            );

            goal.status = "ACTIVE";

        } else if (request.status() != null) {

            Check.require(
                    goal.status.equals(request.status()),
                    "Cannot change status directly"
            );
        }

        if (id == null || request.targetAmount() != null) {

            Check.positive(request.targetAmount());

            if (id != null) {

                Check.require(
                        goal.status.equals("ACTIVE"),
                        "Goal is not active"
                );

                Check.require(
                        request.targetAmount().compareTo(saved(id)) >= 0,
                        "Target amount is less than saved amount"
                );
            }

            goal.targetAmount = request.targetAmount();
        }

        User owner;

        if (id == null || request.ownerId() != null) {

            Check.require(
                    request.ownerId() != null,
                    "Owner is required"
            );

            owner = users.findById(request.ownerId())
                    .orElseThrow(() -> Check.missing("User"));

        } else {
            owner = goal.owner;
        }

        MoneyAcc account;

        if (id == null || request.accountId() != null) {

            Check.require(
                    request.accountId() != null,
                    "Account is required"
            );

            account = accounts.findById(request.accountId())
                    .orElseThrow(() -> Check.missing("Account"));

        } else {
            account = goal.account;
        }

        if (!access.isOwner(owner.id)
                || !access.isOwner(account.owner.id)) {

            throw new AccessDeniedException("Access denied");
        }

        Check.require(
                owner.id.equals(account.owner.id),
                "Goal owner must own account"
        );

        boolean referenced = false;

        if (id != null) {

            for (FinancialTrans operation : transactions.findAll()) {

                if (operation.goal != null
                        && operation.goal.id.equals(id)) {

                    referenced = true;
                }
            }
        }

        if (referenced) {

            Check.require(
                    goal.account.id.equals(account.id),
                    "Cannot change account after contributions"
            );

            Check.require(
                    goal.owner.id.equals(owner.id),
                    "Cannot change goal owner"
            );
        }

        goal.owner = owner;
        goal.account = account;

        return goals.save(goal);
    }

    public BigDecimal saved(Long id) {

        get(id);

        BigDecimal total = BigDecimal.ZERO;

        for (FinancialTrans operation : transactions.findAll()) {

            if (operation.goal == null
                    || !operation.goal.id.equals(id)) {
                continue;
            }

            if (operation.type == TransactionType.CONTRIBUTION
                    && operation.status == TransactionStatus.CONFIRMED) {

                total = total.add(operation.amount);
            }
        }

        return total;
    }

    public void delete(Long id) {

        get(id);

        for (FinancialTrans operation : new ArrayList<>(transactions.findAll())) {

            if (operation.goal != null
                    && operation.goal.id.equals(id)) {

                transactions.delete(operation);
            }
        }

        goals.deleteById(id);
    }

    public FinancialTrans addMoney(Long goalId, BigDecimal amount) {

        Check.positive(amount);

        SavingsGoal goal = get(goalId);
        MoneyAcc account = goal.account;

        if (!access.isOwner(account.owner.id)) {
            throw new AccessDeniedException("Access denied");
        }

        Check.require(
                goal.status.equals("ACTIVE"),
                "Goal is not active"
        );

        BigDecimal balance = account.initialBalance;
        BigDecimal saved = BigDecimal.ZERO;

        for (FinancialTrans t : transactions.findAll()) {

            if (t.status != TransactionStatus.CONFIRMED) {
                continue;
            }

            if (t.account.id.equals(account.id)) {

                if (t.type == TransactionType.INCOME) {
                    balance = balance.add(t.amount);
                } else {
                    balance = balance.subtract(t.amount);
                }
            }

            if (t.type == TransactionType.CONTRIBUTION
                    && t.goal != null
                    && t.goal.id.equals(goalId)) {

                saved = saved.add(t.amount);
            }
        }

        Check.require(
                balance.compareTo(amount) >= 0,
                "Not enough money"
        );

        Check.require(
                saved.add(amount).compareTo(goal.targetAmount) <= 0,
                "Goal amount exceeded"
        );

        FinancialTrans operation = new FinancialTrans();

        operation.amount = amount;
        operation.type = TransactionType.CONTRIBUTION;
        operation.status = TransactionStatus.CONFIRMED;
        operation.createdAt = LocalDateTime.now();
        operation.account = account;
        operation.goal = goal;

        return transactions.save(operation);
    }

    public SavingsGoal completeGoal(Long id) {

        SavingsGoal goal = get(id);

        Check.require(
                goal.status.equals("ACTIVE"),
                "Goal is not active"
        );

        BigDecimal saved = saved(id);

        Check.require(
                saved.compareTo(goal.targetAmount) >= 0,
                "Not enough money saved"
        );

        goal.status = "COMPLETED";

        return goals.save(goal);
    }
}
