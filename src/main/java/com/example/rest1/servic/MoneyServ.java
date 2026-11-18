
package com.example.rest1.servic;

import com.example.rest1.model.*;
import com.example.rest1.pip.*;
import com.example.rest1.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.*;

@Service
@Transactional
public class MoneyServ {

    private final MoneyRepo accounts;
    private final UserRepo users;
    private final FinancialTransRepo transactions;
    private final SavingsRepo goals;
    private final AccessServ access;

    public MoneyServ(
            MoneyRepo accounts,
            UserRepo users,
            FinancialTransRepo transactions,
            SavingsRepo goals,
            AccessServ access
    ) {
        this.accounts = accounts;
        this.users = users;
        this.transactions = transactions;
        this.goals = goals;
        this.access = access;
    }

    public List<MoneyAcc> all() {

        List<MoneyAcc> result = new ArrayList<>();

        for (MoneyAcc account : accounts.findAll()) {
            if (access.isOwner(account.owner.id)) {
                result.add(account);
            }
        }

        return result;
    }

    public MoneyAcc get(Long id) {

        MoneyAcc account = accounts.findById(id)
                .orElseThrow(() -> Check.missing("Account"));

        if (!access.isOwner(account.owner.id)) {
            throw new AccessDeniedException("Access denied");
        }

        return account;
    }

    public MoneyAcc save(Long id, MoneyReq request) {

        MoneyAcc account;

        if (id == null) {
            account = new MoneyAcc();
        } else {
            account = get(id);
        }

        if (id == null || request.name() != null) {
            Check.text(request.name());
            account.name = request.name();
        }

        if (id == null || request.initialBalance() != null) {
            Check.nonnegative(request.initialBalance());
            account.initialBalance = request.initialBalance();
        }

        if (id == null || request.ownerId() != null) {

            Check.require(
                    request.ownerId() != null,
                    "Owner is required"
            );

            User owner = users.findById(request.ownerId())
                    .orElseThrow(() -> Check.missing("User"));

            if (!access.isOwner(owner.id)) {
                throw new AccessDeniedException("Access denied");
            }

            boolean referenced = false;

            if (id != null) {

                for (FinancialTrans operation : transactions.findAll()) {
                    if (operation.account.id.equals(id)) {
                        referenced = true;
                    }
                }

                for (SavingsGoal goal : goals.findAll()) {
                    if (goal.account.id.equals(id)) {
                        referenced = true;
                    }
                }

                if (referenced) {
                    Check.require(
                            account.owner.id.equals(owner.id),
                            "Cannot change owner of referenced account"
                    );
                }
            }

            account.owner = owner;
        }

        return accounts.save(account);
    }

    public BigDecimal balance(Long id) {

        MoneyAcc a = get(id);
        BigDecimal balance = a.initialBalance;

        for (FinancialTrans t : transactions.findAll()) {

            if (!t.account.id.equals(id)
                    || t.status != TransactionStatus.CONFIRMED) {
                continue;
            }

            if (t.type == TransactionType.INCOME) {
                balance = balance.add(t.amount);
            } else {
                balance = balance.subtract(t.amount);
            }
        }

        return balance;
    }

    public void delete(Long id) {

        get(id);

        for (FinancialTrans operation : new ArrayList<>(transactions.findAll())) {
            if (operation.account.id.equals(id)) {
                transactions.delete(operation);
            }
        }

        for (SavingsGoal goal : new ArrayList<>(goals.findAll())) {

            if (goal.account.id.equals(id)) {

                for (FinancialTrans operation : new ArrayList<>(transactions.findAll())) {

                    if (operation.goal != null
                            && operation.goal.id.equals(goal.id)) {
                        transactions.delete(operation);
                    }
                }

                goals.delete(goal);
            }
        }

        accounts.deleteById(id);
    }
}
