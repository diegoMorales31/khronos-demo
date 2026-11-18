package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.response.BusinessSummaryResponseDTO;
import com.moga.khronosdemo.dtos.response.CashClosingResponseDTO;
import com.moga.khronosdemo.dtos.response.ExpenseResponseDTO;
import com.moga.khronosdemo.dtos.response.HoursReportResponseDTO;
import com.moga.khronosdemo.dtos.response.ProductResponseDTO;
import com.moga.khronosdemo.dtos.response.StockMovementResponseDTO;
import com.moga.khronosdemo.dtos.response.SummaryDayResponseDTO;
import com.moga.khronosdemo.dtos.response.WorkerHoursResponseDTO;
import com.moga.khronosdemo.entities.TimeEntry;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.enums.CashClosingStatus;
import com.moga.khronosdemo.entities.enums.DayClosingStatus;
import com.moga.khronosdemo.entities.enums.ExpenseCategory;
import com.moga.khronosdemo.entities.enums.PaymentMethod;
import com.moga.khronosdemo.entities.enums.ProductCategory;
import com.moga.khronosdemo.entities.enums.ProductUnit;
import com.moga.khronosdemo.entities.enums.StockMovementType;
import com.moga.khronosdemo.entities.enums.TimeEntrySource;
import com.moga.khronosdemo.entities.enums.TimeEntryStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.moga.khronosdemo.services.impl.CsvWriter.text;

/**
 * The CSV exports, with Spanish headers and values for spreadsheets set to Spanish: dates as
 * dd/MM/yyyy, times in the establishment's time zone and amounts and quantities with a decimal comma.
 * Text that users typed goes through CsvWriter.text, so it can't run as a formula.
 */
final class ReportCsvFormatter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final String[] HOURS_HEADER = {
            "ID trabajador", "Nombre", "Apellidos", "Activo", "Minutos planificados", "Minutos trabajados",
            "Diferencia (min)", "Turnos", "Fichajes", "Retrasos", "Turnos sin fichar", "Fichajes sin turno",
            "Fichajes abiertos", "Tarifa actual (EUR/h)", "Coste planificado (EUR)", "Coste real (EUR)"
    };

    private static final String[] TIME_ENTRIES_HEADER = {
            "Nombre", "Apellidos", "Día de servicio", "Entrada", "Salida", "Pausa (min)", "Horas trabajadas",
            "Origen", "Corregido", "Estado"
    };

    private static final String[] CASH_CLOSINGS_HEADER = {
            "Día de servicio", "Estado", "Fondo inicial (EUR)", "Ventas en efectivo (EUR)", "Ventas con tarjeta (EUR)",
            "Otras ventas (EUR)", "Ventas totales (EUR)", "Gastos en efectivo (EUR)", "Retiradas de efectivo (EUR)",
            "Efectivo esperado (EUR)", "Efectivo contado (EUR)", "Descuadre (EUR)", "Motivo del descuadre",
            "Propinas (EUR)", "Notas", "Cerrado el", "Cerrado por"
    };

    private static final String[] EXPENSES_HEADER = {
            "Fecha", "Categoría", "Descripción", "Proveedor", "Importe (EUR)", "Medio de pago", "Notas", "Registrado por"
    };

    private static final String[] SUMMARY_HEADER = {
            "Fecha", "Ventas (EUR)", "Gastos (EUR)", "Coste de personal (EUR)", "Margen (EUR)", "Estado del cierre",
            "Descuadre (EUR)"
    };

    private static final String[] INVENTORY_HEADER = {
            "Producto", "Categoría", "Unidad", "Proveedor", "Stock actual", "Stock mínimo", "Bajo mínimo",
            "Coste unitario (EUR)", "Valor del stock (EUR)"
    };

    private static final String[] STOCK_MOVEMENTS_HEADER = {
            "Fecha", "Producto", "Unidad", "Tipo", "Cantidad", "Coste unitario (EUR)", "Valor (EUR)", "Stock después",
            "Motivo", "Pedido", "Registrado el", "Registrado por"
    };

    // One person's entries together, in the order they happened
    private static final Comparator<TimeEntry> BY_PERSON_THEN_CLOCK_IN = Comparator
            .comparing((TimeEntry entry) -> Objects.toString(entry.getWorker().getUser().getFirstName(), ""),
                    String.CASE_INSENSITIVE_ORDER)
            .thenComparing(entry -> Objects.toString(entry.getWorker().getUser().getLastName(), ""),
                    String.CASE_INSENSITIVE_ORDER)
            .thenComparing(entry -> entry.getWorker().getUserId())
            .thenComparing(TimeEntry::getClockIn)
            .thenComparing(TimeEntry::getId);

    private ReportCsvFormatter() {
    }

    static byte[] hours(HoursReportResponseDTO report) {
        CsvWriter csv = new CsvWriter().row(HOURS_HEADER);
        for (WorkerHoursResponseDTO worker : report.workers()) {
            csv.row(
                    String.valueOf(worker.workerId()),
                    text(worker.firstName()),
                    text(worker.lastName()),
                    yesNo(worker.isActive()),
                    String.valueOf(worker.plannedMinutes()),
                    String.valueOf(worker.workedMinutes()),
                    String.valueOf(worker.differenceMinutes()),
                    String.valueOf(worker.shiftsCount()),
                    String.valueOf(worker.entriesCount()),
                    String.valueOf(worker.lateCount()),
                    String.valueOf(worker.missedShiftsCount()),
                    String.valueOf(worker.unplannedCount()),
                    String.valueOf(worker.openEntriesCount()),
                    money(worker.currentHourlyRate()),
                    money(worker.plannedCost()),
                    money(worker.actualCost()));
        }
        return csv.toBytes();
    }

    static byte[] timeEntries(List<TimeEntry> entries, ZoneId zone) {
        CsvWriter csv = new CsvWriter().row(TIME_ENTRIES_HEADER);
        entries.stream().sorted(BY_PERSON_THEN_CLOCK_IN).forEach(entry -> {
            User user = entry.getWorker().getUser();
            csv.row(
                    text(user.getFirstName()),
                    text(user.getLastName()),
                    entry.getBusinessDate().format(DATE),
                    dateTime(entry.getClockIn(), zone),
                    dateTime(entry.getClockOut(), zone),
                    String.valueOf(entry.getBreakMinutes()),
                    hoursAndMinutes(entry.getWorkedMinutes()),
                    entry.getSource() == TimeEntrySource.WORKER ? "Trabajador" : "Responsable",
                    yesNo(entry.isEdited()),
                    status(entry.getStatus()));
        });
        return csv.toBytes();
    }

    // One row per closing, in the order given; closing times in the establishment's time zone
    static byte[] cashClosings(List<CashClosingResponseDTO> closings, ZoneId zone) {
        CsvWriter csv = new CsvWriter().row(CASH_CLOSINGS_HEADER);
        for (CashClosingResponseDTO closing : closings) {
            csv.row(
                    closing.businessDate().format(DATE),
                    closing.status() == CashClosingStatus.CLOSED ? "Cerrado" : "Borrador",
                    money(closing.openingFloat()),
                    money(closing.cashSales()),
                    money(closing.cardSales()),
                    money(closing.otherSales()),
                    money(closing.totalSales()),
                    money(closing.cashExpenses()),
                    money(closing.cashWithdrawals()),
                    money(closing.expectedCash()),
                    money(closing.countedCash()),
                    money(closing.difference()),
                    text(closing.differenceReason()),
                    money(closing.tips()),
                    text(closing.notes()),
                    dateTime(closing.closedAt(), zone),
                    text(closing.closedByName()));
        }
        return csv.toBytes();
    }

    // One row per expense, in the order given
    static byte[] expenses(List<ExpenseResponseDTO> expenses) {
        CsvWriter csv = new CsvWriter().row(EXPENSES_HEADER);
        for (ExpenseResponseDTO expense : expenses) {
            csv.row(
                    expense.date().format(DATE),
                    category(expense.category()),
                    text(expense.description()),
                    text(expense.supplier()),
                    money(expense.amount()),
                    paymentMethod(expense.paymentMethod()),
                    text(expense.notes()),
                    text(expense.createdByName()));
        }
        return csv.toBytes();
    }

    // One row per day of the summary; an unknown staff cost or a day without a closing leaves its cell empty
    static byte[] summary(BusinessSummaryResponseDTO summary) {
        CsvWriter csv = new CsvWriter().row(SUMMARY_HEADER);
        for (SummaryDayResponseDTO day : summary.days()) {
            csv.row(
                    day.date().format(DATE),
                    money(day.sales()),
                    money(day.expenses()),
                    money(day.laborCost()),
                    money(day.margin()),
                    closingStatus(day.closingStatus()),
                    money(day.cashDifference()));
        }
        return csv.toBytes();
    }

    // One row per product, in the order given
    static byte[] inventory(List<ProductResponseDTO> products) {
        CsvWriter csv = new CsvWriter().row(INVENTORY_HEADER);
        for (ProductResponseDTO product : products) {
            csv.row(
                    text(product.name()),
                    category(product.category()),
                    unit(product.unit()),
                    text(product.supplierName()),
                    quantity(product.currentStock()),
                    quantity(product.minStock()),
                    yesNo(product.isLowStock()),
                    money(product.unitCost()),
                    money(product.stockValue()));
        }
        return csv.toBytes();
    }

    // One row per movement, in the order given; the time it was recorded in the establishment's time zone.
    // The value has the quantity's sign (see signedValue)
    static byte[] stockMovements(List<StockMovementResponseDTO> movements, ZoneId zone) {
        CsvWriter csv = new CsvWriter().row(STOCK_MOVEMENTS_HEADER);
        for (StockMovementResponseDTO movement : movements) {
            csv.row(
                    movement.date().format(DATE),
                    text(movement.productName()),
                    unit(movement.unit()),
                    movementType(movement.type()),
                    quantity(movement.quantity()),
                    money(movement.unitCost()),
                    money(signedValue(movement)),
                    quantity(movement.stockAfter()),
                    text(movement.reason()),
                    movement.purchaseOrderId() != null ? String.valueOf(movement.purchaseOrderId()) : "",
                    dateTime(movement.createdAt(), zone),
                    text(movement.createdByName()));
        }
        return csv.toBytes();
    }

    // A movement's value is |quantity| x unitCost in the JSON. In the export it takes the quantity's sign,
    // negative for what went out, so adding up the column gives the inventory report's signed values: a
    // count that found 1.5 units fewer at 12.00 is -18,00 in both
    private static BigDecimal signedValue(StockMovementResponseDTO movement) {
        BigDecimal value = movement.value();
        return value != null && movement.quantity().signum() < 0 ? value.negate() : value;
    }

    private static String dateTime(Instant instant, ZoneId zone) {
        return instant != null ? instant.atZone(zone).format(DATE_TIME) : "";
    }

    // 462 minutes are "7:42"; empty while the entry is open
    private static String hoursAndMinutes(Integer minutes) {
        return minutes != null ? "%d:%02d".formatted(minutes / 60, minutes % 60) : "";
    }

    private static String money(BigDecimal amount) {
        return amount != null ? amount.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',') : "";
    }

    // Without trailing zeros: 2.500 is "2,5" and 3.000 is "3"
    private static String quantity(BigDecimal quantity) {
        return quantity != null ? quantity.stripTrailingZeros().toPlainString().replace('.', ',') : "";
    }

    private static String yesNo(Boolean value) {
        return Boolean.TRUE.equals(value) ? "Sí" : "No";
    }

    private static String category(ExpenseCategory category) {
        return switch (category) {
            case SUPPLIES -> "Género y bebidas";
            case RENT -> "Alquiler";
            case UTILITIES -> "Suministros";
            case PAYROLL -> "Nóminas y seguros sociales";
            case MAINTENANCE -> "Mantenimiento";
            case MARKETING -> "Marketing";
            case TAXES -> "Impuestos";
            case OTHER -> "Otros";
        };
    }

    private static String category(ProductCategory category) {
        return switch (category) {
            case SPIRITS -> "Destilados";
            case BEER -> "Cerveza";
            case WINE -> "Vino";
            case SOFT_DRINKS -> "Refrescos y agua";
            case COFFEE -> "Café";
            case FOOD -> "Comida";
            case SUPPLIES -> "Material";
            case OTHER -> "Otros";
        };
    }

    private static String unit(ProductUnit unit) {
        return switch (unit) {
            case BOTTLE -> "Botella";
            case UNIT -> "Unidad";
            case KG -> "kg";
            case L -> "l";
            case BOX -> "Caja";
            case KEG -> "Barril";
        };
    }

    private static String movementType(StockMovementType type) {
        return switch (type) {
            case PURCHASE -> "Compra";
            case WASTE -> "Merma";
            case CONSUMPTION -> "Consumo";
            case ADJUSTMENT -> "Ajuste";
            case COUNT -> "Recuento";
        };
    }

    private static String paymentMethod(PaymentMethod paymentMethod) {
        return switch (paymentMethod) {
            case CASH -> "Efectivo";
            case CARD -> "Tarjeta";
            case TRANSFER -> "Transferencia";
            case OTHER -> "Otro";
        };
    }

    private static String closingStatus(DayClosingStatus status) {
        return switch (status) {
            case CLOSED -> "Cerrado";
            case DRAFT -> "Borrador";
            case MISSING -> "Falta el cierre";
            case NONE -> "Sin cierre";
        };
    }

    private static String status(TimeEntryStatus status) {
        return switch (status) {
            case OPEN -> "Abierto";
            case CLOSED -> "Cerrado";
            case VOIDED -> "Anulado";
        };
    }
}
