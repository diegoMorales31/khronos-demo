package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.request.ProductRequestDTO;
import com.moga.khronosdemo.dtos.response.ErrorCode;
import com.moga.khronosdemo.dtos.response.ProductResponseDTO;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.Product;
import com.moga.khronosdemo.entities.Supplier;
import com.moga.khronosdemo.exceptions.BusinessRuleException;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.mappers.ProductMapper;
import com.moga.khronosdemo.repositories.EstablishmentRepository;
import com.moga.khronosdemo.repositories.ProductRepository;
import com.moga.khronosdemo.repositories.PurchaseOrderRepository;
import com.moga.khronosdemo.repositories.StockMovementRepository;
import com.moga.khronosdemo.repositories.SupplierRepository;
import com.moga.khronosdemo.services.ProductService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An establishment's products. A product's stock is never edited here: it is the sum of its movements
 * (see StockLedger), worked out for every product of a listing in one query. Changes that depend on
 * whether a product has movements or purchase order lines (its unit, deleting it) and deactivating it take
 * the product's row lock first, as recording a movement and adding an order line do, so neither can slip in
 * between the check and the change.
 */
@Service
@Transactional
public class ProductServiceImpl implements ProductService {

    private static final String UNIQUE_NAME_INDEX = "ux_products_establishment_name";

    // By category in the order the enum lists them, then by name whatever its case; also the inventory report's
    static final Comparator<Product> BY_CATEGORY_THEN_NAME = Comparator
            .comparing(Product::getCategory)
            .thenComparing(Product::getName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Product::getId);

    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final EstablishmentRepository establishmentRepository;
    private final StockLedger stockLedger;
    private final ProductMapper productMapper;

    public ProductServiceImpl(ProductRepository productRepository, StockMovementRepository stockMovementRepository,
                              PurchaseOrderRepository purchaseOrderRepository, SupplierRepository supplierRepository,
                              EstablishmentRepository establishmentRepository, StockLedger stockLedger,
                              ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.supplierRepository = supplierRepository;
        this.establishmentRepository = establishmentRepository;
        this.stockLedger = stockLedger;
        this.productMapper = productMapper;
    }

    // Two queries whatever the number of products: the products with their suppliers, and their stock
    @Override
    @Transactional(readOnly = true)
    public List<ProductResponseDTO> findByEstablishment(Long establishmentId, boolean includeInactive) {
        Map<Long, BigDecimal> stock = stockLedger.stockOfEstablishment(establishmentId);
        return productRepository.findByEstablishment(establishmentId, includeInactive).stream()
                .sorted(BY_CATEGORY_THEN_NAME)
                .map(product -> productMapper.toResponse(product, StockLedger.stockIn(stock, product.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponseDTO findById(Long id) {
        Product product = productRepository.findWithSupplierById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        return productMapper.toResponse(product, stockLedger.stockOf(id));
    }

    @Override
    public ProductResponseDTO create(ProductRequestDTO request) {
        Establishment establishment = establishmentRepository.findById(request.establishmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Establishment", request.establishmentId()));
        checkNameFree(establishment.getId(), request.name(), null);
        Supplier supplier = supplierOf(establishment.getId(), request.supplierId());

        Product product = new Product(establishment);
        productMapper.updateEntity(product, request);
        product.setSupplier(supplier);
        // A new product has no movements yet
        return productMapper.toResponse(save(product), BigDecimal.ZERO);
    }

    // The establishment never changes: the one in the request is ignored. Every check runs before the
    // product changes, so no query flushes a half-checked change
    @Override
    public ProductResponseDTO update(Long id, ProductRequestDTO request) {
        Product product = stockLedger.lock(id);
        Long establishmentId = product.getEstablishment().getId();
        checkNameFree(establishmentId, request.name(), id);
        Supplier supplier = supplierOf(establishmentId, request.supplierId());
        // An order line, in any status, was counted in the unit it has now: receiving it would bring the
        // ordered quantity in under the new unit
        if (request.unit() != product.getUnit() && (stockMovementRepository.existsByProduct_Id(id)
                || purchaseOrderRepository.existsByLines_Product_Id(id))) {
            throw new BusinessRuleException("The unit of a product with stock movements or purchase order lines"
                    + " cannot be changed", ErrorCode.PRODUCT_UNIT_LOCKED);
        }

        productMapper.updateEntity(product, request);
        product.setSupplier(supplier);
        return productMapper.toResponse(save(product), stockLedger.stockOf(id));
    }

    // Repeating it changes nothing
    @Override
    public ProductResponseDTO setActive(Long id, boolean active) {
        Product product = stockLedger.lock(id);
        product.setActive(active);
        return productMapper.toResponse(product, stockLedger.stockOf(id));
    }

    // Only a product nothing was recorded or ordered for can go; one with a history is deactivated instead.
    // An order line is added under the same lock, so none can slip in before the delete
    @Override
    public void delete(Long id) {
        Product product = stockLedger.lock(id);
        if (stockMovementRepository.existsByProduct_Id(id) || purchaseOrderRepository.existsByLines_Product_Id(id)) {
            throw new BusinessRuleException("Cannot delete a product that has stock movements or purchase order"
                    + " lines; deactivate it instead"
                    + " (PUT /api/products/" + id + "/active) to keep its history", ErrorCode.PRODUCT_HAS_HISTORY);
        }
        productRepository.delete(product);
    }

    private void checkNameFree(Long establishmentId, String name, Long excludedId) {
        if (productRepository.existsByName(establishmentId, name.strip(), excludedId)) {
            throw nameTaken(name.strip());
        }
    }

    // The usual supplier must be one of the establishment's own
    private Supplier supplierOf(Long establishmentId, Long supplierId) {
        if (supplierId == null) {
            return null;
        }
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", supplierId));
        if (!Objects.equals(supplier.getEstablishment().getId(), establishmentId)) {
            throw new BusinessRuleException("The supplier belongs to another establishment",
                    ErrorCode.OTHER_ESTABLISHMENT);
        }
        return supplier;
    }

    private Product save(Product product) {
        try {
            return productRepository.saveAndFlush(product);
        } catch (DataIntegrityViolationException ex) {
            // Only reachable if another request saved the same name after the check
            if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains(UNIQUE_NAME_INDEX)) {
                throw nameTaken(product.getName());
            }
            throw ex;
        }
    }

    private static BusinessRuleException nameTaken(String name) {
        return new BusinessRuleException("A product named '" + name + "' already exists at this establishment",
                ErrorCode.PRODUCT_NAME_TAKEN);
    }
}
