package com.expensetracker.service;

import com.expensetracker.dto.SummaryResponse;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.model.Expense;
import com.expensetracker.repository.ExpenseRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional
public class ExpenseService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ExpenseRepository repository;

    public ExpenseService(ExpenseRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Expense> search(String category, LocalDate from, LocalDate to, String keyword) {
        Sort sort = Sort.by(Sort.Direction.DESC, "expenseDate", "id");
        return repository.findAll(buildSpec(category, from, to, keyword), sort);
    }

    @Transactional(readOnly = true)
    public Expense get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense " + id + " not found"));
    }

    public Expense create(Expense expense) {
        expense.setId(null);
        normalize(expense);
        return repository.save(expense);
    }

    public Expense update(Long id, Expense data) {
        Expense existing = get(id);
        existing.setAmount(data.getAmount());
        existing.setCategory(data.getCategory());
        existing.setExpenseDate(data.getExpenseDate());
        existing.setDescription(data.getDescription());
        normalize(existing);
        return repository.save(existing);
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Expense " + id + " not found");
        }
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<String> categories() {
        return repository.findAll().stream()
                .map(Expense::getCategory)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Transactional(readOnly = true)
    public SummaryResponse summary(String category, LocalDate from, LocalDate to, String keyword) {
        List<Expense> list = search(category, from, to, keyword);

        BigDecimal total = BigDecimal.ZERO;
        Map<String, BigDecimal> catTotals = new HashMap<>();
        Map<String, BigDecimal> byMonth = new TreeMap<>();
        for (Expense e : list) {
            total = total.add(e.getAmount());
            catTotals.merge(e.getCategory(), e.getAmount(), BigDecimal::add);
            byMonth.merge(e.getExpenseDate().format(MONTH), e.getAmount(), BigDecimal::add);
        }
        BigDecimal avg = list.isEmpty() ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(list.size()), 2, RoundingMode.HALF_UP);

        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        catTotals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(e -> byCategory.put(e.getKey(), e.getValue()));

        return new SummaryResponse(total, list.size(), avg, byCategory, byMonth);
    }

    private void normalize(Expense e) {
        e.setCategory(e.getCategory().trim());
        if (e.getDescription() != null) {
            String d = e.getDescription().trim();
            e.setDescription(d.isEmpty() ? null : d);
        }
    }

    private Specification<Expense> buildSpec(String category, LocalDate from, LocalDate to, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (category != null && !category.isBlank()) {
                p.add(cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase()));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("expenseDate"), from));
            }
            if (to != null) {
                p.add(cb.lessThanOrEqualTo(root.<LocalDate>get("expenseDate"), to));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.<String>get("description")), like),
                        cb.like(cb.lower(root.<String>get("category")), like)));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
