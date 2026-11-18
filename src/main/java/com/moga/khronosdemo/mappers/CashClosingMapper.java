package com.moga.khronosdemo.mappers;

import com.moga.khronosdemo.dtos.request.CashClosingRequestDTO;
import com.moga.khronosdemo.dtos.response.CashClosingChangeResponseDTO;
import com.moga.khronosdemo.dtos.response.CashClosingResponseDTO;
import com.moga.khronosdemo.entities.CashClosing;
import com.moga.khronosdemo.entities.CashClosingChange;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

import static com.moga.khronosdemo.entities.Money.euros;

@Component
public class CashClosingMapper {

    // The figures come from CashClosing.figures, so a draft can be given the day's current cash expenses.
    // The establishment id comes from the foreign key without loading the establishment.
    public CashClosingResponseDTO toResponse(CashClosing closing, CashClosing.Figures figures) {
        if (closing == null) {
            return null;
        }
        return new CashClosingResponseDTO(
                closing.getId(),
                closing.getEstablishment().getId(),
                closing.getBusinessDate(),
                closing.getStatus(),
                closing.getOpeningFloat(),
                closing.getCashSales(),
                closing.getCardSales(),
                closing.getOtherSales(),
                closing.getCashWithdrawals(),
                closing.getCountedCash(),
                closing.getTips(),
                closing.getNotes(),
                figures.cashExpenses(),
                figures.totalSales(),
                figures.expectedCash(),
                figures.difference(),
                closing.getDifferenceReason(),
                closing.getClosedAt(),
                closing.getClosedByName(),
                closing.getCreatedAt(),
                closing.getUpdatedAt()
        );
    }

    public CashClosingChangeResponseDTO toResponse(CashClosingChange change) {
        if (change == null) {
            return null;
        }
        return new CashClosingChangeResponseDTO(
                change.getId(),
                change.getAction(),
                change.getChangedAt(),
                change.getChangedBy() != null ? change.getChangedBy().getId() : null,
                change.getChangedByName(),
                change.getReason(),
                change.getTotalSales(),
                change.getCountedCash(),
                change.getDifference()
        );
    }

    // The amounts entered; the establishment and the business day are set once, when the closing is created.
    // Amounts are validated to two decimals, so setting the scale never rounds them. A null countedCash
    // leaves the draft without counted cash, also when it had some before, as PUT replaces every amount.
    public void updateEntity(CashClosing closing, CashClosingRequestDTO request) {
        closing.setOpeningFloat(euros(request.openingFloat()));
        closing.setCashSales(euros(request.cashSales()));
        closing.setCardSales(euros(request.cardSales()));
        closing.setOtherSales(euros(request.otherSales() != null ? request.otherSales() : BigDecimal.ZERO));
        closing.setCashWithdrawals(euros(request.cashWithdrawals() != null ? request.cashWithdrawals() : BigDecimal.ZERO));
        closing.setCountedCash(request.countedCash() != null ? euros(request.countedCash()) : null);
        closing.setTips(request.tips() != null ? euros(request.tips()) : null);
        closing.setNotes(StringUtils.hasText(request.notes()) ? request.notes().strip() : null);
    }
}
