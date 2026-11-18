package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.request.StockCountItemRequestDTO;
import com.moga.khronosdemo.dtos.request.StockCountRequestDTO;
import com.moga.khronosdemo.dtos.response.StockMovementResponseDTO;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.Product;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.enums.StockMovementType;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.mappers.StockMovementMapper;
import com.moga.khronosdemo.repositories.EstablishmentRepository;
import com.moga.khronosdemo.repositories.UserRepository;
import com.moga.khronosdemo.services.StockCountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A physical stock count. For each product counted it records a COUNT movement with the difference
 * between what was counted and the stock at that moment, also when they match (a 0 leaves on record that
 * the product was counted), so the stock becomes what was counted. The whole count is one transaction
 * under the products' row locks, taken in id order by StockLedger.lockActive, which also checks they are
 * the establishment's own and active: a movement sent meanwhile either lands before the count, which then
 * counts against it, or waits for the count to finish.
 */
@Service
@Transactional
public class StockCountServiceImpl implements StockCountService {

    private final EstablishmentRepository establishmentRepository;
    private final UserRepository userRepository;
    private final StockLedger stockLedger;
    private final StockMovementMapper stockMovementMapper;

    public StockCountServiceImpl(EstablishmentRepository establishmentRepository, UserRepository userRepository,
                                 StockLedger stockLedger, StockMovementMapper stockMovementMapper) {
        this.establishmentRepository = establishmentRepository;
        this.userRepository = userRepository;
        this.stockLedger = stockLedger;
        this.stockMovementMapper = stockMovementMapper;
    }

    // The request is already validated: 1 to 500 items, each product once. All or nothing: an item whose
    // difference, or the stock it leaves, doesn't fit (see StockLedger.outOfRange) answers 400 with that
    // item's countedQuantity in fieldErrors, every such item at once, and nothing is recorded
    @Override
    public List<StockMovementResponseDTO> count(StockCountRequestDTO request, Long actorId) {
        Establishment establishment = establishmentRepository.findById(request.establishmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Establishment", request.establishmentId()));
        LocalDate date = stockLedger.businessDay(request.date(), establishment,
                "The count date cannot be in the future");

        List<Long> productIds = request.items().stream().map(StockCountItemRequestDTO::productId).toList();
        Map<Long, Product> products = stockLedger.lockActive(establishment, productIds);

        Map<Long, BigDecimal> stock = stockLedger.stockOf(productIds);
        List<BigDecimal> differences = new ArrayList<>();
        Map<String, String> outOfRange = new LinkedHashMap<>();
        for (int i = 0; i < request.items().size(); i++) {
            StockCountItemRequestDTO item = request.items().get(i);
            BigDecimal before = stock.get(item.productId());
            BigDecimal difference = item.countedQuantity().setScale(3, RoundingMode.UNNECESSARY).subtract(before);
            String error = StockLedger.outOfRange(difference, before);
            if (error != null) {
                outOfRange.put("items[" + i + "].countedQuantity", error);
            }
            differences.add(difference);
        }
        StockLedger.rejectOutOfRange(outOfRange);

        User author = getUser(actorId);
        String reason = StringUtils.hasText(request.notes()) ? request.notes().strip() : null;
        List<StockMovementResponseDTO> recorded = new ArrayList<>();
        for (int i = 0; i < request.items().size(); i++) {
            Product product = products.get(request.items().get(i).productId());
            recorded.add(stockMovementMapper.toResponse(stockLedger.record(product, StockMovementType.COUNT,
                    differences.get(i), product.getUnitCost(), reason, date, stock.get(product.getId()), author)));
        }
        return recorded;
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
