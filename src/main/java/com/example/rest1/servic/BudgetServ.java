
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
public class BudgetServ {

    private final BudgetRepo categories;
    private final UserRepo users;
    private final FinancialTransRepo transactions;
    private final AccessServ access;

    public BudgetServ(
            BudgetRepo categories,
            UserRepo users,
            FinancialTransRepo transactions,
            AccessServ access
    ) {
        this.categories = categories;
        this.users = users;
        this.transactions = transactions;
        this.access = access;
    }

    public List<BudgetCategory> all() {

        List<BudgetCategory> result = new ArrayList<>();

        for (BudgetCategory category : categories.findAll()) {
            if (access.isOwner(category.owner.id)) {
                result.add(category);
            }
        }

        return result;
    }

    public BudgetCategory get(Long id) {

        BudgetCategory category = categories.findById(id)
                .orElseThrow(() -> Check.missing("Category"));

        if (!access.isOwner(category.owner.id)) {
            throw new AccessDeniedException("Access denied");
        }

        return category;
    }

    public BudgetCategory save(Long id, BudgetRequest request) {

        BudgetCategory category;

        if (id == null) {
            category = new BudgetCategory();
        } else {
            category = get(id);
        }

        if (id == null || request.name() != null) {
            Check.text(request.name());
            category.name = request.name();
        }

        if (id == null || request.monthlyExpenseLimit() != null) {

            Check.nonnegative(request.monthlyExpenseLimit());

            if (id != null) {

                LocalDateTime now = LocalDateTime.now();

                BigDecimal total = spent(
                        id,
                        now.getYear(),
                        now.getMonthValue()
                );

                Check.require(
                        request.monthlyExpenseLimit().compareTo(total) >= 0,
                        "Limit is less than current expenses"
                );
            }

            category.monthlyExpenseLimit = request.monthlyExpenseLimit();
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

                    if (operation.category != null
                            && operation.category.id.equals(id)) {

                        referenced = true;
                    }
                }
            }

            if (referenced) {
                Check.require(
                        category.owner.id.equals(owner.id),
                        "Cannot change category owner"
                );
            }

            category.owner = owner;
        }

        return categories.save(category);
    }

    public BigDecimal spent(Long id, int year, int month) {

        get(id);

        Check.require(
                month >= 1 && month <= 12,
                "Invalid month"
        );

        BigDecimal total = BigDecimal.ZERO;

        for (FinancialTrans operation : transactions.findAll()) {

            if (operation.category == null
                    || !operation.category.id.equals(id)) {
                continue;
            }

            if (operation.type == TransactionType.EXPENSE
                    && operation.status == TransactionStatus.CONFIRMED
                    && operation.createdAt.getYear() == year
                    && operation.createdAt.getMonthValue() == month) {

                total = total.add(operation.amount);
            }
        }

        return total;
    }

    public void delete(Long id) {

        get(id);

        for (FinancialTrans operation : new ArrayList<>(transactions.findAll())) {

            if (operation.category != null
                    && operation.category.id.equals(id)) {

                transactions.delete(operation);
            }
        }

        categories.deleteById(id);
    }

    public BudgetCategory changeLimit(Long id, BigDecimal newLimit) {

        BudgetCategory category = get(id);

        Check.nonnegative(newLimit);

        LocalDateTime now = LocalDateTime.now();

        BigDecimal total = spent(
                id,
                now.getYear(),
                now.getMonthValue()
        );

        Check.require(
                newLimit.compareTo(total) >= 0,
                "New limit is less than current expenses"
        );

        category.monthlyExpenseLimit = newLimit;

        return categories.save(category);
    }
}
