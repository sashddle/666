
package com.example.rest1.servic;

import com.example.rest1.model.*;
import com.example.rest1.pip.*;
import com.example.rest1.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.*;

@Service
@Transactional
@PreAuthorize("hasRole('ADMIN')")
public class UserServ {

    private final UserRepo users;
    private final MoneyServ accounts;
    private final BudgetServ categories;
    private final SavingServ goals;
    private final AccessServ access;

    public UserServ(
            UserRepo users,
            MoneyServ accounts,
            BudgetServ categories,
            SavingServ goals,
            AccessServ access
    ) {
        this.users = users;
        this.accounts = accounts;
        this.categories = categories;
        this.goals = goals;
        this.access = access;
    }

    public List<User> all() {
        return users.findAll();
    }

    public User get(Long id) {

        return users.findById(id)
                .orElseThrow(() -> Check.missing("User"));
    }

    public User save(Long id, Userreq request) {

        User user;

        if (id == null) {

            user = new User();
            user.role = Role.USER;
            user.enabled = false;

        } else {
            user = get(id);
        }

        if (id == null || request.name() != null) {

            Check.text(request.name());
            user.name = request.name();
        }

        if (id == null || request.email() != null) {

            Check.text(request.email());

            String email = request.email()
                    .trim()
                    .toLowerCase(Locale.ROOT);

            Check.require(
                    email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"),
                    "Invalid email"
            );

            User existing = users.findByEmailIgnoreCase(email)
                    .orElse(null);

            Check.require(
                    existing == null || existing.id.equals(id),
                    "Email already exists"
            );

            user.email = email;
        }

        return users.save(user);
    }

    public void delete(Long id) {

        User user = get(id);

        Check.require(
                !access.currentUser().id.equals(id),
                "Cannot delete yourself"
        );

        Check.require(
                user.role != Role.ADMIN,
                "Cannot delete administrator"
        );

        for (MoneyAcc account : new ArrayList<>(accounts.all())) {

            if (account.owner.id.equals(id)) {
                accounts.delete(account.id);
            }
        }

        for (BudgetCategory category : new ArrayList<>(categories.all())) {

            if (category.owner.id.equals(id)) {
                categories.delete(category.id);
            }
        }

        for (SavingsGoal goal : new ArrayList<>(goals.all())) {

            if (goal.owner.id.equals(id)) {
                goals.delete(goal.id);
            }
        }

        users.deleteById(id);
    }
}
