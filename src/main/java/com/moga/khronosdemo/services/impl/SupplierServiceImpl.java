package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.request.SupplierRequestDTO;
import com.moga.khronosdemo.dtos.response.ErrorCode;
import com.moga.khronosdemo.dtos.response.SupplierResponseDTO;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.Supplier;
import com.moga.khronosdemo.exceptions.BusinessRuleException;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.mappers.SupplierMapper;
import com.moga.khronosdemo.repositories.EstablishmentRepository;
import com.moga.khronosdemo.repositories.ProductRepository;
import com.moga.khronosdemo.repositories.PurchaseOrderRepository;
import com.moga.khronosdemo.repositories.SupplierRepository;
import com.moga.khronosdemo.services.SupplierService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * An establishment's suppliers. Names are unique within the establishment whatever their case: the
 * service checks first, and the unique index on lower(name) answers the same 409 if two requests race.
 * A supplier with purchase orders is kept with them; deleting one without clears it from its products.
 * The same goes for an order created while the supplier is being deleted: its foreign key
 * (fk_purchase_orders_supplier, ON DELETE RESTRICT) refuses the delete, and that answers the same 409.
 */
@Service
@Transactional
public class SupplierServiceImpl implements SupplierService {

    private static final String UNIQUE_NAME_INDEX = "ux_suppliers_establishment_name";
    private static final String ORDERS_FOREIGN_KEY = "fk_purchase_orders_supplier";

    private final SupplierRepository supplierRepository;
    private final EstablishmentRepository establishmentRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProductRepository productRepository;
    private final SupplierMapper supplierMapper;

    public SupplierServiceImpl(SupplierRepository supplierRepository, EstablishmentRepository establishmentRepository,
                               PurchaseOrderRepository purchaseOrderRepository, ProductRepository productRepository,
                               SupplierMapper supplierMapper) {
        this.supplierRepository = supplierRepository;
        this.establishmentRepository = establishmentRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.productRepository = productRepository;
        this.supplierMapper = supplierMapper;
    }

    // By name, whatever its case
    @Override
    @Transactional(readOnly = true)
    public List<SupplierResponseDTO> findByEstablishment(Long establishmentId) {
        return supplierRepository.findByEstablishment(establishmentId).stream()
                .map(supplierMapper::toResponse)
                .toList();
    }

    @Override
    public SupplierResponseDTO create(SupplierRequestDTO request) {
        Establishment establishment = establishmentRepository.findById(request.establishmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Establishment", request.establishmentId()));
        checkNameFree(establishment.getId(), request.name(), null);
        Supplier supplier = new Supplier(establishment);
        supplierMapper.updateEntity(supplier, request);
        return save(supplier);
    }

    // The establishment never changes: the one in the request is ignored
    @Override
    public SupplierResponseDTO update(Long id, SupplierRequestDTO request) {
        Supplier supplier = getSupplier(id);
        checkNameFree(supplier.getEstablishment().getId(), request.name(), id);
        supplierMapper.updateEntity(supplier, request);
        return save(supplier);
    }

    // The supplier is cleared from its products, as the database would (ON DELETE SET NULL), so their locks
    // come first, in the lock order every request follows (see StockLedger): an order of one of them that is
    // being created commits before the check, which then sees it. An order of other products that is saved
    // but not committed holds a key-share lock on the supplier's row, which the delete waits for; once the
    // order commits, its foreign key refuses the delete. The delete is flushed here, so that answers the
    // same 409 instead of failing at commit. Locking the supplier's row wouldn't do: such a lock is FOR NO
    // KEY UPDATE (see EstablishmentRepository.lockById), which an order's foreign key check doesn't wait for
    @Override
    public void delete(Long id) {
        Supplier supplier = getSupplier(id);
        productRepository.lockBySupplier(id).forEach(product -> product.setSupplier(null));
        if (purchaseOrderRepository.existsBySupplier_Id(id)) {
            throw hasOrders();
        }
        try {
            supplierRepository.delete(supplier);
            supplierRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains(ORDERS_FOREIGN_KEY)) {
                throw hasOrders();
            }
            throw ex;
        }
    }

    // Checked before the entity changes, so the query doesn't flush a duplicate name first
    private void checkNameFree(Long establishmentId, String name, Long excludedId) {
        if (supplierRepository.existsByName(establishmentId, name.strip(), excludedId)) {
            throw nameTaken(name.strip());
        }
    }

    private SupplierResponseDTO save(Supplier supplier) {
        try {
            return supplierMapper.toResponse(supplierRepository.saveAndFlush(supplier));
        } catch (DataIntegrityViolationException ex) {
            // Only reachable if another request saved the same name after the check above
            if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains(UNIQUE_NAME_INDEX)) {
                throw nameTaken(supplier.getName());
            }
            throw ex;
        }
    }

    private Supplier getSupplier(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", id));
    }

    private static BusinessRuleException hasOrders() {
        return new BusinessRuleException("Cannot delete a supplier that has purchase orders",
                ErrorCode.SUPPLIER_HAS_ORDERS);
    }

    private static BusinessRuleException nameTaken(String name) {
        return new BusinessRuleException("A supplier named '" + name + "' already exists at this establishment",
                ErrorCode.SUPPLIER_NAME_TAKEN);
    }
}
