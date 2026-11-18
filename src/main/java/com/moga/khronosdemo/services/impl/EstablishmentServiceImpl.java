package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.request.EstablishmentRequestDTO;
import com.moga.khronosdemo.dtos.response.EstablishmentResponseDTO;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.enums.Role;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.exceptions.BusinessRuleException;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.mappers.EstablishmentMapper;
import com.moga.khronosdemo.repositories.CashClosingRepository;
import com.moga.khronosdemo.repositories.EstablishmentRepository;
import com.moga.khronosdemo.repositories.ExpenseRepository;
import com.moga.khronosdemo.repositories.ProductRepository;
import com.moga.khronosdemo.repositories.PurchaseOrderRepository;
import com.moga.khronosdemo.repositories.StockMovementRepository;
import com.moga.khronosdemo.repositories.TimeEntryRepository;
import com.moga.khronosdemo.repositories.UserRepository;
import com.moga.khronosdemo.services.EstablishmentService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class EstablishmentServiceImpl implements EstablishmentService {

    // The foreign keys (ON DELETE RESTRICT) that keep an establishment's inventory record: those of its stock
    // movements and purchase orders, also through the products and suppliers that would go with it
    private static final List<String> INVENTORY_FOREIGN_KEYS = List.of("fk_stock_movements_establishment",
            "fk_stock_movements_product", "fk_purchase_orders_establishment", "fk_purchase_orders_supplier",
            "fk_purchase_order_lines_product");

    private final EstablishmentRepository establishmentRepository;
    private final UserRepository userRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final CashClosingRepository cashClosingRepository;
    private final ExpenseRepository expenseRepository;
    private final StockMovementRepository stockMovementRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProductRepository productRepository;
    private final EstablishmentMapper establishmentMapper;

    public EstablishmentServiceImpl(EstablishmentRepository establishmentRepository, UserRepository userRepository,
                                    TimeEntryRepository timeEntryRepository, CashClosingRepository cashClosingRepository,
                                    ExpenseRepository expenseRepository, StockMovementRepository stockMovementRepository,
                                    PurchaseOrderRepository purchaseOrderRepository,
                                    ProductRepository productRepository, EstablishmentMapper establishmentMapper) {
        this.establishmentRepository = establishmentRepository;
        this.userRepository = userRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.cashClosingRepository = cashClosingRepository;
        this.expenseRepository = expenseRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.productRepository = productRepository;
        this.establishmentMapper = establishmentMapper;
    }

    @Override
    public EstablishmentResponseDTO create(EstablishmentRequestDTO request) {
        if (establishmentRepository.existsByEstablishmentAddress(request.establishmentAddress())) {
            throw new BusinessRuleException("An establishment already exists at that address");
        }
        Establishment establishment = establishmentMapper.toEntity(request, getOwner(request.ownerId()));
        return establishmentMapper.toResponse(establishmentRepository.save(establishment));
    }

    @Override
    @Transactional(readOnly = true)
    public EstablishmentResponseDTO findById(Long id) {
        return establishmentMapper.toResponse(getEstablishment(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EstablishmentResponseDTO> findAll() {
        return establishmentRepository.findAll().stream().map(establishmentMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EstablishmentResponseDTO> findByOwner(Long ownerId) {
        return establishmentRepository.findByOwner_Id(ownerId).stream().map(establishmentMapper::toResponse).toList();
    }

    @Override
    public EstablishmentResponseDTO update(Long id, EstablishmentRequestDTO request) {

        Establishment establishment = getEstablishment(id);

        if (establishmentRepository.existsByEstablishmentAddressAndIdNot(request.establishmentAddress(), id)) {
            throw new BusinessRuleException("An establishment already exists at that address");

        }
        establishmentMapper.updateEntity(establishment, request, getOwner(request.ownerId()));

        return establishmentMapper.toResponse(establishment);

    }

    // Deleting an establishment deletes its workers, so it is not allowed while it has time entries. Its cash
    // closings (drafts too) and expenses are its financial record, and its stock movements and purchase
    // orders its inventory record, so they keep it from being deleted as well. Products without movements
    // and suppliers without orders go with it.
    // The checks run under the establishment's lock and then its products', in the lock order every request
    // follows (see StockLedger): a cash closing, expense, movement, count or order of it that is being
    // recorded commits first, and the checks see it, or waits for the delete. A product added meanwhile is
    // not locked, so a movement or order of it can still commit before the delete: the foreign key that
    // refuses the delete then answers the same 409, as it is flushed here rather than at commit
    @Override
    public void delete(Long id) {
        Establishment establishment = establishmentRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Establishment", id));
        productRepository.lockByEstablishment(id);
        if (timeEntryRepository.existsByEstablishment_Id(id)) {
            throw new BusinessRuleException("Cannot delete an establishment that has time entries;"
                    + " deactivate its workers instead to keep the time entries");
        }
        if (cashClosingRepository.existsByEstablishment_Id(id) || expenseRepository.existsByEstablishment_Id(id)) {
            throw new BusinessRuleException("Cannot delete an establishment that has cash closings or expenses;"
                    + " keep it to keep its financial records");
        }
        if (stockMovementRepository.existsByEstablishment_Id(id) || purchaseOrderRepository.existsByEstablishment_Id(id)) {
            throw hasInventoryRecords();
        }
        try {
            establishmentRepository.delete(establishment);
            establishmentRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            String cause = String.valueOf(ex.getMostSpecificCause().getMessage());
            if (INVENTORY_FOREIGN_KEYS.stream().anyMatch(cause::contains)) {
                throw hasInventoryRecords();
            }
            throw ex;
        }
    }

    private static BusinessRuleException hasInventoryRecords() {
        return new BusinessRuleException("Cannot delete an establishment that has stock movements or purchase orders;"
                + " keep it to keep its inventory records");
    }

    private Establishment getEstablishment(Long id) {
        return establishmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Establishment", id));
    }

    private User getOwner(Long ownerId) {

        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", ownerId));

        if (owner.getRole() != Role.OWNER) {
            throw new BusinessRuleException("The owner of an establishment must have the OWNER role");

        }

        return owner;

    }
}
