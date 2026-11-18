package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.request.ExpenseRequestDTO;
import com.moga.khronosdemo.dtos.request.PurchaseOrderLineRequestDTO;
import com.moga.khronosdemo.dtos.request.PurchaseOrderReceiveLineRequestDTO;
import com.moga.khronosdemo.dtos.request.PurchaseOrderReceiveRequestDTO;
import com.moga.khronosdemo.dtos.request.PurchaseOrderRequestDTO;
import com.moga.khronosdemo.dtos.response.ErrorCode;
import com.moga.khronosdemo.dtos.response.ExpenseResponseDTO;
import com.moga.khronosdemo.dtos.response.PurchaseOrderResponseDTO;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.Product;
import com.moga.khronosdemo.entities.PurchaseOrder;
import com.moga.khronosdemo.entities.PurchaseOrderLine;
import com.moga.khronosdemo.entities.Supplier;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.enums.ExpenseCategory;
import com.moga.khronosdemo.entities.enums.PurchaseOrderStatus;
import com.moga.khronosdemo.entities.enums.StockMovementType;
import com.moga.khronosdemo.exceptions.BusinessRuleException;
import com.moga.khronosdemo.exceptions.InvalidRequestException;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.mappers.PurchaseOrderMapper;
import com.moga.khronosdemo.repositories.EstablishmentRepository;
import com.moga.khronosdemo.repositories.ExpenseRepository;
import com.moga.khronosdemo.repositories.PurchaseOrderRepository;
import com.moga.khronosdemo.repositories.SupplierRepository;
import com.moga.khronosdemo.repositories.UserRepository;
import com.moga.khronosdemo.services.ExpenseService;
import com.moga.khronosdemo.services.PurchaseOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Purchase orders to suppliers: DRAFT -> ORDERED -> RECEIVED, or CANCELLED from DRAFT or ORDERED. Only a
 * draft can be edited or deleted. Any other move answers 409 PURCHASE_ORDER_WRONG_STATUS.
 * <p>
 * Receiving an order is one transaction: it records a PURCHASE movement for each line that came in,
 * updates the cost of each product received with a cost and, if asked, creates the expense through
 * ExpenseService, with its own checks. Each line that came in is valued at its own cost; one without a
 * cost takes its product's and keeps it, so the line totals, the order's total, the movements' values and
 * the expense all agree. When that expense is in cash and its day's cash closing is closed,
 * ExpenseService refuses it with its own 409 (CASH_DAY_CLOSED, the day in details.date) and nothing at all
 * is received.
 * <p>
 * Every change locks the order's row first, so two changes to one order take turns, and then its
 * products in id order, checked through StockLedger.lockActive. Receiving locks the establishment before
 * both, through CashRecordLock as cash closings and expenses do, since the expense it may create needs that
 * lock and the lock order is establishment, order, products (see StockLedger).
 * <p>
 * Deleting the expense later (DELETE /api/expenses/{id}) keeps the order received with its movements;
 * the database only clears its expense_id. Editing it is refused (see ExpenseServiceImpl), so while it is
 * linked its amount stays the order's total.
 */
@Service
@Transactional
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private static final BigDecimal MAX_EXPENSE = new BigDecimal("99999999.99");

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final EstablishmentRepository establishmentRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final ExpenseService expenseService;
    private final CashRecordLock cashRecordLock;
    private final StockLedger stockLedger;
    private final PurchaseOrderMapper purchaseOrderMapper;

    public PurchaseOrderServiceImpl(PurchaseOrderRepository purchaseOrderRepository,
                                    SupplierRepository supplierRepository,
                                    EstablishmentRepository establishmentRepository,
                                    ExpenseRepository expenseRepository, UserRepository userRepository,
                                    ExpenseService expenseService, CashRecordLock cashRecordLock,
                                    StockLedger stockLedger, PurchaseOrderMapper purchaseOrderMapper) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.supplierRepository = supplierRepository;
        this.establishmentRepository = establishmentRepository;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.expenseService = expenseService;
        this.cashRecordLock = cashRecordLock;
        this.stockLedger = stockLedger;
        this.purchaseOrderMapper = purchaseOrderMapper;
    }

    // Newest first. from and to are days of creation in the establishment's time zone, both included, and
    // cover at most 366 days (see DateRange.capped): without dates, the last 366 up to today. Only then does
    // the establishment have to exist; with dates, one that doesn't has no orders
    @Override
    @Transactional(readOnly = true)
    public List<PurchaseOrderResponseDTO> findByEstablishment(Long establishmentId, PurchaseOrderStatus status,
                                                              LocalDate from, LocalDate to) {
        Optional<Establishment> establishment = establishmentRepository.findById(establishmentId);
        DateRange range = DateRange.capped(from, to, ReportServiceImpl.MAX_DAYS, () -> stockLedger.today(establishment
                .orElseThrow(() -> new ResourceNotFoundException("Establishment", establishmentId))));
        if (establishment.isEmpty()) {
            return List.of();
        }
        ZoneId zone = establishment.get().getZoneId();
        Instant start = range.from().atStartOfDay(zone).toInstant();
        Instant until = range.to().plusDays(1).atStartOfDay(zone).toInstant();
        return purchaseOrderRepository.findByEstablishment(establishmentId, status, start, until).stream()
                .map(purchaseOrderMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseOrderResponseDTO findById(Long id) {
        return purchaseOrderMapper.toResponse(purchaseOrderRepository.findWithLinesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", id)));
    }

    @Override
    public PurchaseOrderResponseDTO create(PurchaseOrderRequestDTO request, Long actorId) {
        Establishment establishment = establishmentRepository.findById(request.establishmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Establishment", request.establishmentId()));
        Supplier supplier = supplierOf(establishment, request.supplierId());
        Map<Long, Product> products = stockLedger.lockActive(establishment, request.lines().stream()
                .map(PurchaseOrderLineRequestDTO::productId).toList());

        PurchaseOrder order = new PurchaseOrder(establishment, getUser(actorId), stockLedger.auditNow());
        fill(order, request, supplier, products);
        purchaseOrderRepository.saveAndFlush(order);
        return response(order.getId());
    }

    // Replaces the supplier, dates, notes and lines; the establishment in the request is ignored
    @Override
    public PurchaseOrderResponseDTO update(Long id, PurchaseOrderRequestDTO request) {
        PurchaseOrder order = lock(id);
        requireStatus(order, "Only a draft purchase order can be edited", PurchaseOrderStatus.DRAFT);
        Establishment establishment = order.getEstablishment();
        Supplier supplier = supplierOf(establishment, request.supplierId());
        Map<Long, Product> products = stockLedger.lockActive(establishment, request.lines().stream()
                .map(PurchaseOrderLineRequestDTO::productId).toList());

        // The old lines go before the new ones are added, so a product kept on the order never has two lines
        order.getLines().clear();
        purchaseOrderRepository.flush();
        fill(order, request, supplier, products);
        purchaseOrderRepository.flush();
        return response(id);
    }

    @Override
    public void delete(Long id) {
        PurchaseOrder order = lock(id);
        requireStatus(order, "Only a draft purchase order can be deleted", PurchaseOrderStatus.DRAFT);
        purchaseOrderRepository.delete(order);
    }

    // Sent to the supplier today, in the establishment's time zone. Its products must still be active
    @Override
    public PurchaseOrderResponseDTO order(Long id) {
        PurchaseOrder order = lock(id);
        requireStatus(order, "Only a draft purchase order can be ordered", PurchaseOrderStatus.DRAFT);
        stockLedger.lockActive(order.getEstablishment(), productIds(order.getLines()));
        order.setStatus(PurchaseOrderStatus.ORDERED);
        order.setOrderDate(stockLedger.today(order.getEstablishment()));
        return response(id);
    }

    @Override
    public PurchaseOrderResponseDTO cancel(Long id) {
        PurchaseOrder order = lock(id);
        requireStatus(order, "Only a draft or ordered purchase order can be cancelled",
                PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.ORDERED);
        order.setStatus(PurchaseOrderStatus.CANCELLED);
        return response(id);
    }

    @Override
    public PurchaseOrderResponseDTO receive(Long id, PurchaseOrderReceiveRequestDTO request, Long actorId) {
        // Lock order: establishment, order, products (see StockLedger)
        Long establishmentId = purchaseOrderRepository.findEstablishmentIdById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", id));
        Establishment establishment = cashRecordLock.lockEstablishment(establishmentId);
        PurchaseOrder order = lock(id);
        requireStatus(order, "Only an ordered purchase order can be received", PurchaseOrderStatus.ORDERED);
        LocalDate receivedDate = stockLedger.businessDay(request.receivedDate(), establishment,
                "The received date cannot be in the future");

        Map<Long, PurchaseOrderReceiveLineRequestDTO> received = receivedLines(order, request);
        List<PurchaseOrderLine> arrived = order.getLines().stream()
                .filter(line -> received.get(line.getId()).receivedQuantity().signum() > 0)
                .sorted(Comparator.comparing(PurchaseOrderLine::getId))
                .toList();
        if (arrived.isEmpty()) {
            throw new BusinessRuleException("Nothing was received; cancel the purchase order instead",
                    ErrorCode.PURCHASE_ORDER_NOTHING_RECEIVED);
        }
        // Only the products that came in must still be active
        Map<Long, Product> products = stockLedger.lockActive(establishment, productIds(arrived));

        for (PurchaseOrderLine line : order.getLines()) {
            PurchaseOrderReceiveLineRequestDTO receipt = received.get(line.getId());
            line.setReceivedQuantity(receipt.receivedQuantity().setScale(3, RoundingMode.UNNECESSARY));
            if (receipt.unitCost() != null) {
                line.setUnitCost(receipt.unitCost().setScale(2, RoundingMode.UNNECESSARY));
            }
        }
        // A line that came in without a cost of its own is valued at its product's cost, read under the
        // product's lock, and keeps it: its lineTotal, the order's total, its movement's value and the
        // expense then all agree. Without a cost anywhere it stays without one, outside the total
        for (PurchaseOrderLine line : arrived) {
            if (line.getUnitCost() == null) {
                line.setUnitCost(products.get(line.getProduct().getId()).getUnitCost());
            }
        }
        // The stock each line leaves must fit before anything is recorded (see StockLedger.outOfRange)
        Map<Long, BigDecimal> stock = stockLedger.stockOf(products.keySet());
        checkStockInRange(arrived, stock, request);

        User author = getUser(actorId);
        // The expense goes first: if ExpenseService refuses it, nothing has been recorded yet, and the
        // whole transaction is rolled back anyway
        if (Boolean.TRUE.equals(request.createExpense())) {
            order.setExpense(expenseRepository.getReferenceById(
                    createExpense(order, receivedDate, request, author).id()));
        }

        for (PurchaseOrderLine line : arrived) {
            Product product = products.get(line.getProduct().getId());
            // The movement is valued at the line's cost, which becomes the product's (the same one when the
            // line took it from the product)
            if (line.getUnitCost() != null) {
                product.setUnitCost(line.getUnitCost());
            }
            stockLedger.record(product, StockMovementType.PURCHASE, line.getReceivedQuantity(), line.getUnitCost(),
                    null, receivedDate, stock.get(product.getId()), order, author);
        }
        order.setStatus(PurchaseOrderStatus.RECEIVED);
        order.setReceivedDate(receivedDate);
        return response(id);
    }

    // An expense of the SUPPLIES category for the total received, on the day it arrived. ExpenseService
    // applies its own rules, among them the closed cash day, and its 409 goes out as it is
    private ExpenseResponseDTO createExpense(PurchaseOrder order, LocalDate receivedDate,
                                             PurchaseOrderReceiveRequestDTO request, User author) {
        BigDecimal total = order.total();
        if (total == null || total.signum() == 0) {
            throw new BusinessRuleException("The received purchase order has no total to record as an expense;"
                    + " give its lines a unit cost or receive it without an expense", ErrorCode.PURCHASE_ORDER_NO_TOTAL);
        }
        if (total.compareTo(MAX_EXPENSE) > 0) {
            throw new BusinessRuleException("The received purchase order's total of " + total.toPlainString()
                    + " EUR is larger than an expense can be");
        }
        return expenseService.create(new ExpenseRequestDTO(order.getEstablishment().getId(), receivedDate,
                ExpenseCategory.SUPPLIES, "Pedido #" + order.getId(), order.getSupplier().getName(), total,
                request.paymentMethod(), null), author.getId());
    }

    // 400 with lines[i].receivedQuantity, i being the line's place in the request, for every line whose
    // stock after it would be out of range
    private static void checkStockInRange(List<PurchaseOrderLine> arrived, Map<Long, BigDecimal> stock,
                                          PurchaseOrderReceiveRequestDTO request) {
        Map<Long, Integer> requestIndex = new HashMap<>();
        for (int i = 0; i < request.lines().size(); i++) {
            requestIndex.put(request.lines().get(i).lineId(), i);
        }
        Map<String, String> outOfRange = new LinkedHashMap<>();
        for (PurchaseOrderLine line : arrived) {
            String error = StockLedger.outOfRange(line.getReceivedQuantity(), stock.get(line.getProduct().getId()));
            if (error != null) {
                outOfRange.put("lines[" + requestIndex.get(line.getId()) + "].receivedQuantity", error);
            }
        }
        StockLedger.rejectOutOfRange(outOfRange);
    }

    // Each line of the order, exactly once: the request already has no line twice
    private static Map<Long, PurchaseOrderReceiveLineRequestDTO> receivedLines(PurchaseOrder order,
                                                                              PurchaseOrderReceiveRequestDTO request) {
        Map<Long, PurchaseOrderReceiveLineRequestDTO> received = request.lines().stream()
                .collect(Collectors.toMap(PurchaseOrderReceiveLineRequestDTO::lineId, Function.identity()));
        Set<Long> lineIds = order.getLines().stream().map(PurchaseOrderLine::getId).collect(Collectors.toSet());
        for (Long lineId : received.keySet()) {
            if (!lineIds.contains(lineId)) {
                throw new InvalidRequestException("Line " + lineId + " is not a line of this purchase order");
            }
        }
        lineIds.stream()
                .filter(lineId -> !received.containsKey(lineId))
                .sorted()
                .findFirst()
                .ifPresent(missing -> {
                    throw new InvalidRequestException("Every line of the purchase order must be sent; line "
                            + missing + " is missing");
                });
        return received;
    }

    private void fill(PurchaseOrder order, PurchaseOrderRequestDTO request, Supplier supplier,
                      Map<Long, Product> products) {
        order.setSupplier(supplier);
        order.setExpectedDate(request.expectedDate());
        order.setNotes(StringUtils.hasText(request.notes()) ? request.notes().strip() : null);
        for (PurchaseOrderLineRequestDTO line : request.lines()) {
            order.getLines().add(new PurchaseOrderLine(order, products.get(line.productId()),
                    line.quantity().setScale(3, RoundingMode.UNNECESSARY),
                    line.unitCost() != null ? line.unitCost().setScale(2, RoundingMode.UNNECESSARY) : null));
        }
    }

    private Supplier supplierOf(Establishment establishment, Long supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", supplierId));
        if (!Objects.equals(supplier.getEstablishment().getId(), establishment.getId())) {
            throw new BusinessRuleException("The supplier belongs to another establishment",
                    ErrorCode.OTHER_ESTABLISHMENT);
        }
        return supplier;
    }

    private static List<Long> productIds(List<PurchaseOrderLine> lines) {
        return lines.stream().map(line -> line.getProduct().getId()).toList();
    }

    private static void requireStatus(PurchaseOrder order, String rule, PurchaseOrderStatus... allowed) {
        if (!EnumSet.of(allowed[0], allowed).contains(order.getStatus())) {
            throw new BusinessRuleException(rule + "; this one is " + order.getStatus(),
                    ErrorCode.PURCHASE_ORDER_WRONG_STATUS);
        }
    }

    private PurchaseOrder lock(Long id) {
        return purchaseOrderRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", id));
    }

    // The order as saved, with its supplier, lines and products read in one query
    private PurchaseOrderResponseDTO response(Long id) {
        return findById(id);
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
