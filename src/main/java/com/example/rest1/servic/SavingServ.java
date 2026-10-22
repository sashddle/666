package com.example.rest1.servic;

import com.example.rest1.model.*;
import com.example.rest1.pip.*;
import com.example.rest1.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class SavingServ {
    private final SavingsRepo goals;
    private final UserRepo users;
    private final MoneyRepo accounts;
    private final FinancialTransRepo transactions;
    public SavingServ(SavingsRepo goals,UserRepo users,MoneyRepo accounts,FinancialTransRepo transactions) {
        this.goals = goals;
        this.users = users;
        this.accounts = accounts;
        this.transactions = transactions;
    }
    public List<SavingsGoal> all() {
        return goals.findAll();
    }
    public SavingsGoal get(Long id) {
        return goals.findById(id == null ? -1L : id).orElseThrow(() -> Check.missing("Goal"));
    }
    public SavingsGoal save(Long id, SavingReq request) {
        SavingsGoal goal = id == null ? new SavingsGoal() : get(id);
        if (id == null || request.name()!=null) {
            Check.text(request.name());
            goal.name = request.name();
        }
        if (id == null || request.status()!=null) {
            Check.text(request.status());
            goal.status = request.status();
        }
        if (id == null || request.targetAmount()!=null) {
            Check.positive(request.targetAmount());
            goal.targetAmount = request.targetAmount();
        }
        User owner = (id == null||request.ownerId()!=null) ? users.findById(request.ownerId()==null ? -1 : request.ownerId()).orElseThrow(() -> Check.missing("User")) : goal.owner;
        MoneyAcc account = (id == null||request.accountId()!=null) ? accounts.findById(request.accountId()==null ? -1 : request.accountId()).orElseThrow(() -> Check.missing("Account")) : goal.account;
        Check.require(owner.id.equals(account.owner.id),"Goal owner must own account");
        boolean referenced = false;
        if (id != null) {
            for (FinancialTrans operation : transactions.findAll()) {
                if (operation.goal != null && operation.goal.id.equals(id)) {
                    referenced = true;
                }
            }
        }
        if (referenced) {
            Check.require(goal.account.id.equals(account.id),"Cannot change account after contributions");
        }
        goal.owner = owner;
        goal.account = account;
        return goals.save(goal);
    }
    public BigDecimal saved(Long id) {
        get(id);
        BigDecimal total = BigDecimal.ZERO;
        for (FinancialTrans operation : transactions.findAll()) {
            if (operation.goal == null || !operation.goal.id.equals(id)) {
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
            if (operation.goal != null && operation.goal.id.equals(id)) {
                transactions.delete(operation);
            }
        }
        goals.deleteById(id);
    }

    public FinancialTrans addMoney(Long goalId, BigDecimal amount) {

        Check.positive(amount);

        SavingsGoal goal = get(goalId);
        MoneyAcc account = goal.account;

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

        SavingsGoal goal = goals.findById(id)
                .orElseThrow(() -> Check.missing("Goal"));

        Check.require(
                goal.status.equals("ACTIVE"),
                "Goal is not active"
        );

        BigDecimal saved = BigDecimal.ZERO;

        for (FinancialTrans t : transactions.findAll()) {

            if (t.type == TransactionType.CONTRIBUTION
                    && t.status == TransactionStatus.CONFIRMED
                    && t.goal != null
                    && t.goal.id.equals(id)) {

                saved = saved.add(t.amount);
            }
        }

        Check.require(
                saved.compareTo(goal.targetAmount) >= 0,
                "Not enough money saved"
        );

        goal.status = "COMPLETED";

        return goals.save(goal);
    }

}
