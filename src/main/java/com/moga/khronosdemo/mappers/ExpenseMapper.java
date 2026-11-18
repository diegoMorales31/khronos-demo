package com.moga.khronosdemo.mappers;

import com.moga.khronosdemo.dtos.request.ExpenseRequestDTO;
import com.moga.khronosdemo.dtos.response.ExpenseResponseDTO;
import com.moga.khronosdemo.entities.Expense;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import static com.moga.khronosdemo.entities.Money.euros;

@Component
public class ExpenseMapper {

    // The establishment id comes from the foreign key without loading the establishment. purchaseOrderId is
    // the order whose receipt created the expense, or null; callers look it up, a listing in one query
    public ExpenseResponseDTO toResponse(Expense expense, Long purchaseOrderId) {
        if (expense == null) {
            return null;
        }
        return new ExpenseResponseDTO(
                expense.getId(),
                expense.getEstablishment().getId(),
                expense.getDate(),
                expense.getCategory(),
                expense.getDescription(),
                expense.getSupplier(),
                expense.getAmount(),
                expense.getPaymentMethod(),
                expense.getNotes(),
                expense.getCreatedAt(),
                expense.getCreatedByName(),
                purchaseOrderId
        );
    }

    // Everything but the establishment, which an update ignores. The amount is validated to two decimals,
    // so setting the scale never rounds it; it only makes 12.5 come back as 12.50 straight away.
    public void updateEntity(Expense expense, ExpenseRequestDTO request) {
        expense.setDate(request.date());
        expense.setCategory(request.category());
        expense.setDescription(request.description().strip());
        expense.setSupplier(clean(request.supplier()));
        expense.setAmount(euros(request.amount()));
        expense.setPaymentMethod(request.paymentMethod());
        expense.setNotes(clean(request.notes()));
    }

    private static String clean(String text) {
        return StringUtils.hasText(text) ? text.strip() : null;
    }
}
