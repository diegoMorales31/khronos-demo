package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.request.ChangePasswordRequestDTO;
import com.moga.khronosdemo.dtos.request.PasswordResetRequestDTO;
import com.moga.khronosdemo.dtos.request.UserRequestDTO;
import com.moga.khronosdemo.dtos.request.UserUpdateRequestDTO;
import com.moga.khronosdemo.dtos.response.UserResponseDTO;
import com.moga.khronosdemo.entities.enums.Role;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.exceptions.BusinessRuleException;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.mappers.UserMapper;
import com.moga.khronosdemo.repositories.CashClosingRepository;
import com.moga.khronosdemo.repositories.EstablishmentRepository;
import com.moga.khronosdemo.repositories.ExpenseRepository;
import com.moga.khronosdemo.repositories.PurchaseOrderRepository;
import com.moga.khronosdemo.repositories.StockMovementRepository;
import com.moga.khronosdemo.repositories.TimeEntryRepository;
import com.moga.khronosdemo.repositories.UserRepository;
import com.moga.khronosdemo.repositories.WorkerRepository;
import com.moga.khronosdemo.services.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final WorkerRepository workerRepository;
    private final EstablishmentRepository establishmentRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final CashClosingRepository cashClosingRepository;
    private final ExpenseRepository expenseRepository;
    private final StockMovementRepository stockMovementRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, WorkerRepository workerRepository,
                           EstablishmentRepository establishmentRepository, TimeEntryRepository timeEntryRepository,
                           CashClosingRepository cashClosingRepository, ExpenseRepository expenseRepository,
                           StockMovementRepository stockMovementRepository,
                           PurchaseOrderRepository purchaseOrderRepository, UserMapper userMapper,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.workerRepository = workerRepository;
        this.establishmentRepository = establishmentRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.cashClosingRepository = cashClosingRepository;
        this.expenseRepository = expenseRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserResponseDTO createUser(UserRequestDTO request, Role role, Long creatorId) {
        return createWithRole(request, role, creatorId);
    }

    @Override
    public UserResponseDTO createWorker(UserRequestDTO request, Long creatorId) {
        return createWithRole(request, Role.WORKER, creatorId);
    }

    private UserResponseDTO createWithRole(UserRequestDTO request, Role role, Long creatorId) {

        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessRuleException("Username already in use");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessRuleException("Email already in use");
        }

        if (request.phoneNumber() != null && userRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new BusinessRuleException("Phone number already in use");
        }

        User user = userMapper.toEntity(request, role);
        if (creatorId != null) {
            user.setCreatedBy(userRepository.getReferenceById(creatorId));
        }

        return userMapper.toResponse(userRepository.save(user));

    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDTO findById(Long id) {
        return userMapper.toResponse(getUser(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponseDTO> findAll() {
        return userRepository.findAll().stream().map(userMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponseDTO> findByEstablishment(Long establishmentId) {
        return userRepository.findWorkersOfEstablishment(establishmentId).stream().map(userMapper::toResponse).toList();
    }

    @Override
    public UserResponseDTO update(Long id, UserUpdateRequestDTO request) {
        User user = getUser(id);
        if (userRepository.existsByUsernameAndIdNot(request.username(), id)) {
            throw new BusinessRuleException("Username already in use");
        }
        if (userRepository.existsByEmailAndIdNot(request.email(), id)) {
            throw new BusinessRuleException("Email already in use");
        }
        if (request.phoneNumber() != null && userRepository.existsByPhoneNumberAndIdNot(request.phoneNumber(), id)) {
            throw new BusinessRuleException("Phone number already in use");
        }
        userMapper.updateEntity(user, request);
        return userMapper.toResponse(user);
    }

    @Override
    public void changePassword(Long id, ChangePasswordRequestDTO request) {

        User user = getUser(id);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessRuleException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setCredentialsChangedAt(Instant.now());

    }

    // Set by an admin or the worker's owner, so there is no current password to check. As with
    // changePassword, every token issued before the reset stops working.
    @Override
    public void resetPassword(Long id, PasswordResetRequestDTO request) {
        User user = getUser(id);
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setCredentialsChangedAt(Instant.now());
    }

    // Deleting an owner deletes their establishments, and deleting a worker's account deletes the worker,
    // so neither is allowed while it would take time entries with it, nor an owner while it would take
    // cash closings, expenses, stock movements or purchase orders. The author of a change can be deleted:
    // the change keeps their name.
    @Override
    public void delete(Long id) {

        User user = getUser(id);
        if (user.getRole() == Role.PLATFORM_ADMIN) {
            throw new BusinessRuleException("Platform admins cannot be deleted through the API");
        }

        if (user.getRole() == Role.OWNER) {
            if (timeEntryRepository.existsByEstablishment_Owner_Id(id)) {
                throw new BusinessRuleException("Cannot delete an owner whose establishments have time entries;"
                        + " deactivate their workers instead to keep the time entries");
            }
            if (cashClosingRepository.existsByEstablishment_Owner_Id(id)
                    || expenseRepository.existsByEstablishment_Owner_Id(id)) {
                throw new BusinessRuleException("Cannot delete an owner whose establishments have cash closings or"
                        + " expenses; keep the owner to keep those financial records");
            }
            if (stockMovementRepository.existsByEstablishment_Owner_Id(id)
                    || purchaseOrderRepository.existsByEstablishment_Owner_Id(id)) {
                throw new BusinessRuleException("Cannot delete an owner whose establishments have stock movements or"
                        + " purchase orders; keep the owner to keep those inventory records");
            }
            establishmentRepository.deleteAll(establishmentRepository.findByOwner_Id(id));
        }

        workerRepository.findById(id).ifPresentOrElse(
                worker -> {
                    if (timeEntryRepository.existsByWorker_UserId(id)) {
                        throw new BusinessRuleException("Cannot delete the account of a worker who has time entries;"
                                + " deactivate the worker instead (PUT /api/workers/" + id + "/active) to keep"
                                + " their time entries");
                    }
                    workerRepository.delete(worker);
                },
                () -> userRepository.delete(user)
        );

    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
