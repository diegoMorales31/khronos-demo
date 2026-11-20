package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.response.InventoryReportResponseDTO;
import com.moga.khronosdemo.dtos.response.LowStockProductResponseDTO;
import com.moga.khronosdemo.dtos.response.ProductResponseDTO;
import com.moga.khronosdemo.dtos.response.StockMovementResponseDTO;
import com.moga.khronosdemo.dtos.response.WasteByProductResponseDTO;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.Product;
import com.moga.khronosdemo.entities.enums.StockMovementType;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.mappers.ProductMapper;
import com.moga.khronosdemo.repositories.EstablishmentRepository;
import com.moga.khronosdemo.repositories.ProductRepository;
import com.moga.khronosdemo.repositories.StockMovementRepository;
import com.moga.khronosdemo.dtos.response.CsvFileResponseDTO;
import com.moga.khronosdemo.services.InventoryReportService;
import com.moga.khronosdemo.services.ProductService;
import com.moga.khronosdemo.services.StockMovementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The inventory report and the inventory exports. The report runs the same four queries whatever the
 * range and the number of products and movements: the establishment, its products with their suppliers,
 * every product's stock, and the movements of the range added up by product and type in the database.
 * <p>
 * The stock figures are as of now and count only active products; stockValue is each one's stock on
 * hand at its current cost. The values of the range are each movement's quantity times the unit cost it
 * kept, rounded to cents as the movement listing shows it; movements without a cost add nothing.
 */
@Service
@Transactional(readOnly = true)
public class InventoryReportServiceImpl implements InventoryReportService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final int MAX_WASTE_ROWS = 10;
    private static final Comparator<WasteByProductResponseDTO> LARGEST_WASTE_FIRST = Comparator
            .comparing(WasteByProductResponseDTO::value, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(WasteByProductResponseDTO::quantity, Comparator.reverseOrder())
            .thenComparing(WasteByProductResponseDTO::productName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(WasteByProductResponseDTO::productId);

    private final EstablishmentRepository establishmentRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockLedger stockLedger;
    private final ProductService productService;
    private final StockMovementService stockMovementService;

    public InventoryReportServiceImpl(EstablishmentRepository establishmentRepository,
                                      ProductRepository productRepository,
                                      StockMovementRepository stockMovementRepository, StockLedger stockLedger,
                                      ProductService productService, StockMovementService stockMovementService) {
        this.establishmentRepository = establishmentRepository;
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.stockLedger = stockLedger;
        this.productService = productService;
        this.stockMovementService = stockMovementService;
    }

    @Override
    public InventoryReportResponseDTO inventory(Long establishmentId, LocalDate from, LocalDate to) {
        DateRange range = DateRange.bounded(from, to, ReportServiceImpl.MAX_DAYS);
        getEstablishment(establishmentId);
        List<Product> products = productRepository.findByEstablishment(establishmentId, true);
        Map<Long, BigDecimal> stock = stockLedger.stockOfEstablishment(establishmentId);
        List<StockMovementRepository.ProductTypeTotal> totals =
                stockMovementRepository.sumByProductAndType(establishmentId, range.from(), range.to());

        List<Product> active = products.stream()
                .filter(Product::isActive)
                .sorted(ProductServiceImpl.BY_CATEGORY_THEN_NAME)
                .toList();
        BigDecimal stockValue = ZERO;
        int negative = 0;
        List<LowStockProductResponseDTO> lowStock = new ArrayList<>();
        for (Product product : active) {
            BigDecimal current = StockLedger.stockIn(stock, product.getId());
            BigDecimal value = ProductMapper.stockValue(current, product.getUnitCost());
            stockValue = stockValue.add(value != null ? value : ZERO);
            if (current.signum() < 0) {
                negative++;
            }
            if (product.getMinStock() != null && current.compareTo(product.getMinStock()) <= 0) {
                lowStock.add(new LowStockProductResponseDTO(product.getId(), product.getName(), product.getUnit(),
                        current, product.getMinStock(),
                        product.getSupplier() != null ? product.getSupplier().getId() : null,
                        product.getSupplier() != null ? product.getSupplier().getName() : null));
            }
        }

        return new InventoryReportResponseDTO(
                range.from(),
                range.to(),
                ReportServiceImpl.CURRENCY,
                stockValue,
                active.size(),
                lowStock.size(),
                negative,
                valueOf(totals, StockMovementType.PURCHASE),
                valueOf(totals, StockMovementType.WASTE).negate(),
                valueOf(totals, StockMovementType.COUNT),
                wasteByProduct(totals, products),
                lowStock);
    }

    // Named after today in the establishment's time zone, since it is the stock as of now
    @Override
    public CsvFileResponseDTO inventoryCsv(Long establishmentId) {
        Establishment establishment = getEstablishment(establishmentId);
        List<ProductResponseDTO> products = productService.findByEstablishment(establishmentId, false);
        return new CsvFileResponseDTO("inventario_" + stockLedger.today(establishment) + ".csv",
                ReportCsvFormatter.inventory(products));
    }

    // Oldest first, then in the order they were recorded
    @Override
    public byte[] stockMovementsCsv(Long establishmentId, LocalDate from, LocalDate to) {
        DateRange range = DateRange.bounded(from, to, ReportServiceImpl.MAX_DAYS);
        Establishment establishment = getEstablishment(establishmentId);
        List<StockMovementResponseDTO> movements = new ArrayList<>(
                stockMovementService.findByEstablishment(establishmentId, range.from(), range.to(), null, null));
        Collections.reverse(movements);
        return ReportCsvFormatter.stockMovements(movements, establishment.getZoneId());
    }

    // The values of one type added up, signed like their quantities; 0.00 when there are none with a cost
    private static BigDecimal valueOf(List<StockMovementRepository.ProductTypeTotal> totals, StockMovementType type) {
        return totals.stream()
                .filter(total -> total.getType() == type)
                .map(StockMovementRepository.ProductTypeTotal::getValue)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);
    }

    private static List<WasteByProductResponseDTO> wasteByProduct(List<StockMovementRepository.ProductTypeTotal> totals,
                                                                  List<Product> products) {
        Map<Long, Product> byId = products.stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        return totals.stream()
                .filter(total -> total.getType() == StockMovementType.WASTE)
                .map(total -> {
                    Product product = byId.get(total.getProductId());
                    return new WasteByProductResponseDTO(product.getId(), product.getName(), product.getUnit(),
                            total.getQuantity().negate(),
                            total.getValue() != null ? total.getValue().negate() : null);
                })
                .sorted(LARGEST_WASTE_FIRST)
                .limit(MAX_WASTE_ROWS)
                .toList();
    }

    private Establishment getEstablishment(Long id) {
        return establishmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Establishment", id));
    }
}
