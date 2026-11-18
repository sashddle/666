package com.example.rest1.servic;

import com.example.rest1.model.Role;
import com.example.rest1.model.User;
import com.example.rest1.repo.UserRepo;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AccessServ {

    private final UserRepo users;

    public AccessServ(UserRepo users) {
        this.users = users;
    }

    public User currentUser() {

        Authentication auth = SecurityContextHolder
                .getContext()
                .getAuthentication();

        return users.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> Check.missing("User"));
    }

    public boolean isAdmin() {
        return currentUser().role == Role.ADMIN;
    }

    public boolean isOwner(Long ownerId) {

        User user = currentUser();

        if (user.role == Role.ADMIN) {
            return true;
        }

        return user.id.equals(ownerId);
    }
}