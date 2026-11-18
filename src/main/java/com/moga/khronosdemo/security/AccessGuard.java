package com.moga.khronosdemo.security;

import com.moga.khronosdemo.entities.enums.Role;
import com.moga.khronosdemo.repositories.CashClosingRepository;
import com.moga.khronosdemo.repositories.EstablishmentRepository;
import com.moga.khronosdemo.repositories.EventRepository;
import com.moga.khronosdemo.repositories.ExpenseRepository;
import com.moga.khronosdemo.repositories.ProductRepository;
import com.moga.khronosdemo.repositories.PurchaseOrderRepository;
import com.moga.khronosdemo.repositories.SupplierRepository;
import com.moga.khronosdemo.repositories.TimeEntryRepository;
import com.moga.khronosdemo.repositories.UserRepository;
import com.moga.khronosdemo.repositories.WorkerEventRepository;
import com.moga.khronosdemo.repositories.WorkerRepository;
import com.moga.khronosdemo.repositories.WorkerShiftRepository;
import com.moga.khronosdemo.repositories.ZoneRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component("access")
@Transactional(readOnly = true)
public class AccessGuard {

    private final EstablishmentRepository establishmentRepository;
    private final ZoneRepository zoneRepository;
    private final EventRepository eventRepository;
    private final WorkerRepository workerRepository;
    private final WorkerShiftRepository workerShiftRepository;
    private final WorkerEventRepository workerEventRepository;
    private final UserRepository userRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final ExpenseRepository expenseRepository;
    private final CashClosingRepository cashClosingRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    public AccessGuard(EstablishmentRepository establishmentRepository, ZoneRepository zoneRepository,
                       EventRepository eventRepository, WorkerRepository workerRepository,
                       WorkerShiftRepository workerShiftRepository, WorkerEventRepository workerEventRepository,
                       UserRepository userRepository, TimeEntryRepository timeEntryRepository,
                       ExpenseRepository expenseRepository, CashClosingRepository cashClosingRepository,
                       SupplierRepository supplierRepository, ProductRepository productRepository,
                       PurchaseOrderRepository purchaseOrderRepository) {
        this.establishmentRepository = establishmentRepository;
        this.zoneRepository = zoneRepository;
        this.eventRepository = eventRepository;
        this.workerRepository = workerRepository;
        this.workerShiftRepository = workerShiftRepository;
        this.workerEventRepository = workerEventRepository;
        this.userRepository = userRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.expenseRepository = expenseRepository;
        this.cashClosingRepository = cashClosingRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

    public Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SecurityUser user) {
            return user.getId();
        }
        return null;
    }

    public boolean isSelf(Long userId) {
        return userId != null && userId.equals(currentUserId());
    }

    public boolean isPlatformAdmin(Long userId) {
        return userId != null && userRepository.findById(userId)
                .map(user -> user.getRole() == Role.PLATFORM_ADMIN)
                .orElse(false);
    }

    // An unlinked worker account can only be claimed by whoever created it, never by another owner
    public boolean createdUser(Long userId) {
        Long me = currentUserId();
        return me != null && userId != null && userRepository.findById(userId)
                .map(user -> user.getCreatedBy() != null && me.equals(user.getCreatedBy().getId()))
                .orElse(false);
    }

    public boolean ownsEstablishment(Long establishmentId) {
        if (establishmentId == null) {
            return false;
        }
        return establishmentRepository.findById(establishmentId)
                .map(establishment -> isSelf(establishment.getOwner().getId()))
                .orElse(false);
    }

    public boolean worksAt(Long establishmentId) {
        Long me = currentUserId();
        if (me == null || establishmentId == null) {
            return false;
        }
        return workerRepository.findById(me)
                .map(worker -> worker.getEstablishment().getId().equals(establishmentId))
                .orElse(false);
    }

    public boolean memberOfEstablishment(Long establishmentId) {
        return ownsEstablishment(establishmentId) || worksAt(establishmentId);
    }

    public boolean canUpdateEstablishment(Long establishmentId, Long requestedOwnerId) {
        return ownsEstablishment(establishmentId) && isSelf(requestedOwnerId);
    }

    public boolean ownsZone(Long zoneId) {
        return zoneId != null && zoneRepository.findById(zoneId)
                .map(zone -> ownsEstablishment(zone.getEstablishment().getId()))
                .orElse(false);
    }

    public boolean ownsZones(Collection<Long> zoneIds) {
        return zoneIds != null && !zoneIds.isEmpty() && zoneIds.stream().distinct().allMatch(this::ownsZone);
    }

    public boolean memberOfZone(Long zoneId) {
        return zoneId != null && zoneRepository.findById(zoneId)
                .map(zone -> memberOfEstablishment(zone.getEstablishment().getId()))
                .orElse(false);
    }

    public boolean ownsEvent(Long eventId) {
        return eventId != null && eventRepository.findById(eventId)
                .map(event -> ownsEstablishment(event.getEstablishment().getId()))
                .orElse(false);
    }

    public boolean memberOfEvent(Long eventId) {
        return eventId != null && eventRepository.findById(eventId)
                .map(event -> memberOfEstablishment(event.getEstablishment().getId()))
                .orElse(false);
    }

    public boolean ownsWorker(Long workerId) {
        return workerId != null && workerRepository.findById(workerId)
                .map(worker -> ownsEstablishment(worker.getEstablishment().getId()))
                .orElse(false);
    }

    public boolean ownsWorkers(Collection<Long> workerIds) {
        return workerIds != null && !workerIds.isEmpty() && workerIds.stream().distinct().allMatch(this::ownsWorker);
    }

    public boolean ownsEvents(Collection<Long> eventIds) {
        return eventIds != null && !eventIds.isEmpty() && eventIds.stream().distinct().allMatch(this::ownsEvent);
    }

    public boolean ownsShift(Long shiftId) {
        return shiftId != null && workerShiftRepository.findById(shiftId)
                .map(shift -> ownsWorker(shift.getWorker().getUserId()))
                .orElse(false);
    }

    public boolean isOwnShift(Long shiftId) {
        return shiftId != null && workerShiftRepository.findById(shiftId)
                .map(shift -> isSelf(shift.getWorker().getUserId()))
                .orElse(false);
    }

    public boolean ownsWorkerEvent(Long workerEventId) {
        return workerEventId != null && workerEventRepository.findById(workerEventId)
                .map(workerEvent -> ownsWorker(workerEvent.getWorker().getUserId()))
                .orElse(false);
    }

    public boolean isOwnWorkerEvent(Long workerEventId) {
        return workerEventId != null && workerEventRepository.findById(workerEventId)
                .map(workerEvent -> isSelf(workerEvent.getWorker().getUserId()))
                .orElse(false);
    }

    // An entry belongs to the establishment it was recorded at
    public boolean ownsTimeEntry(Long timeEntryId) {
        return timeEntryId != null && timeEntryRepository.findById(timeEntryId)
                .map(entry -> ownsEstablishment(entry.getEstablishment().getId()))
                .orElse(false);
    }

    public boolean isOwnTimeEntry(Long timeEntryId) {
        return timeEntryId != null && timeEntryRepository.findById(timeEntryId)
                .map(entry -> isSelf(entry.getWorker().getUserId()))
                .orElse(false);
    }

    // Expenses belong to the establishment they were entered for, which never changes
    public boolean ownsExpense(Long expenseId) {
        return expenseId != null && expenseRepository.findEstablishmentIdById(expenseId)
                .map(this::ownsEstablishment)
                .orElse(false);
    }

    // A closing belongs to the establishment it was created for, which never changes
    public boolean ownsCashClosing(Long cashClosingId) {
        return cashClosingId != null && cashClosingRepository.findEstablishmentIdById(cashClosingId)
                .map(this::ownsEstablishment)
                .orElse(false);
    }

    // A supplier belongs to the establishment it was created for, which never changes
    public boolean ownsSupplier(Long supplierId) {
        return supplierId != null && supplierRepository.findEstablishmentIdById(supplierId)
                .map(this::ownsEstablishment)
                .orElse(false);
    }

    // A product belongs to the establishment it was created for, which never changes
    public boolean ownsProduct(Long productId) {
        return productId != null && productRepository.findEstablishmentIdById(productId)
                .map(this::ownsEstablishment)
                .orElse(false);
    }

    // Every product exists and is of an establishment the caller owns. One query for the products,
    // however many there are, and one per establishment they belong to
    public boolean ownsProducts(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty() || productIds.stream().anyMatch(Objects::isNull)) {
            return false;
        }
        Set<Long> ids = Set.copyOf(productIds);
        List<ProductRepository.ProductEstablishment> found = productRepository.findEstablishmentIdsByIdIn(ids);
        return found.size() == ids.size() && found.stream()
                .map(ProductRepository.ProductEstablishment::getEstablishmentId)
                .collect(Collectors.toSet())
                .stream()
                .allMatch(this::ownsEstablishment);
    }

    // A purchase order belongs to the establishment it was created for, which never changes
    public boolean ownsPurchaseOrder(Long purchaseOrderId) {
        return purchaseOrderId != null && purchaseOrderRepository.findEstablishmentIdById(purchaseOrderId)
                .map(this::ownsEstablishment)
                .orElse(false);
    }
}
