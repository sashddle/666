
package com.example.rest1.controller;

import com.example.rest1.servic.BudgetServ;
import com.example.rest1.pip.BudgetRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/budget-categories")
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class BudgetCon {

    private final BudgetServ service;

    public BudgetCon(BudgetServ service) {
        this.service = service;
    }

    @GetMapping
    public Object all() {
        return service.all();
    }

    @GetMapping("/{id}")
    public Object one(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody BudgetRequest r) {

        return ResponseEntity.status(201)
                .body(service.save(null, r));
    }

    @PatchMapping("/{id}")
    public Object update(
            @PathVariable Long id,
            @RequestBody BudgetRequest r
    ) {
        return service.save(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @GetMapping("/{id}/spent")
    public Object spent(
            @PathVariable Long id,
            @RequestParam int year,
            @RequestParam int month
    ) {
        return Map.of(
                "categoryId", id,
                "year", year,
                "month", month,
                "spent", service.spent(id, year, month)
        );
    }

    @PatchMapping("/{id}/change-limit")
    public ResponseEntity<?> changeLimit(
            @PathVariable Long id,
            @RequestParam BigDecimal amount
    ) {
        return ResponseEntity.ok(
                service.changeLimit(id, amount)
        );
    }
}
