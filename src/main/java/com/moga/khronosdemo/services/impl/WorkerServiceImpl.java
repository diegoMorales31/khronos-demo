package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.request.WorkerRequestDTO;
import com.moga.khronosdemo.dtos.request.WorkerWithUserRequestDTO;
import com.moga.khronosdemo.dtos.response.UserResponseDTO;
import com.moga.khronosdemo.dtos.response.WorkerResponseDTO;
import com.moga.khronosdemo.dtos.response.WorkerWithUserResponseDTO;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.enums.Role;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.exceptions.BusinessRuleException;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.mappers.WorkerMapper;
import com.moga.khronosdemo.repositories.EstablishmentRepository;
import com.moga.khronosdemo.repositories.PushSubscriptionRepository;
import com.moga.khronosdemo.repositories.RefreshSessionRepository;
import com.moga.khronosdemo.repositories.TimeEntryRepository;
import com.moga.khronosdemo.repositories.UserRepository;
import com.moga.khronosdemo.repositories.WorkerRepository;
import com.moga.khronosdemo.services.UserService;
import com.moga.khronosdemo.services.WorkerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class WorkerServiceImpl implements WorkerService {

    private final WorkerRepository workerRepository;
    private final UserRepository userRepository;
    private final EstablishmentRepository establishmentRepository;
    private final WorkerMapper workerMapper;
    private final UserService userService;
    private final TimeEntryRepository timeEntryRepository;
    private final PlanLimits planLimits;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final RefreshSessionRepository refreshSessionRepository;

    public WorkerServiceImpl(WorkerRepository workerRepository, UserRepository userRepository,
                             EstablishmentRepository establishmentRepository, WorkerMapper workerMapper,
                             UserService userService, TimeEntryRepository timeEntryRepository,
                             PlanLimits planLimits, PushSubscriptionRepository pushSubscriptionRepository,
                             RefreshSessionRepository refreshSessionRepository) {
        this.workerRepository = workerRepository;
        this.userRepository = userRepository;
        this.establishmentRepository = establishmentRepository;
        this.workerMapper = workerMapper;
        this.userService = userService;
        this.timeEntryRepository = timeEntryRepository;
        this.planLimits = planLimits;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.refreshSessionRepository = refreshSessionRepository;
    }

    @Override
    public WorkerResponseDTO create(WorkerRequestDTO request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.userId()));
        if (workerRepository.existsById(user.getId())) {
            throw new BusinessRuleException("User is already a worker");
        }
        if (user.getRole() != Role.WORKER) {
            throw new BusinessRuleException("Only users with the WORKER role can be registered as workers");
        }
        Establishment establishment = getEstablishment(request.establishmentId());
        if (Boolean.TRUE.equals(request.isActive())) {
            planLimits.checkRoomForOneMore(establishment.getOwner().getId());
        }
        Worker worker = workerMapper.toEntity(request, user, establishment);
        return workerMapper.toResponse(workerRepository.save(worker));
    }

    // The account and its link share this transaction, so if linking fails no account is left behind.
    // A new worker is active, so the owner's plan must have room for one more first
    @Override
    public WorkerWithUserResponseDTO createWithUser(WorkerWithUserRequestDTO request, Long creatorId) {
        Establishment establishment = getEstablishment(request.establishmentId());
        planLimits.checkRoomForOneMore(establishment.getOwner().getId());
        UserResponseDTO user = userService.createWorker(request.user(), creatorId);
        Worker worker = new Worker(userRepository.getReferenceById(user.id()), establishment);
        return new WorkerWithUserResponseDTO(user, workerMapper.toResponse(workerRepository.save(worker)));
    }

    @Override
    @Transactional(readOnly = true)
    public WorkerResponseDTO findById(Long userId) {
        return workerMapper.toResponse(getWorker(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkerResponseDTO> findAll() {
        return workerRepository.findAll().stream().map(workerMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkerResponseDTO> findByEstablishment(Long establishmentId) {
        return workerRepository.findByEstablishment_Id(establishmentId).stream().map(workerMapper::toResponse).toList();
    }

    // The worker's lock makes a clock-in sent meanwhile wait: either it commits first and the check for
    // time entries below sees it, or it runs after the move and is recorded at the new establishment
    @Override
    public WorkerResponseDTO update(Long userId, WorkerRequestDTO request) {
        Worker worker = lockWorker(userId);
        if (!userId.equals(request.userId())) {
            throw new BusinessRuleException("The user of a worker cannot be changed");
        }
        boolean changesEstablishment = !worker.getEstablishment().getId().equals(request.establishmentId());
        if (changesEstablishment && (!worker.getWorkerShifts().isEmpty() || !worker.getWorkerEvents().isEmpty())) {
            throw new BusinessRuleException("Cannot move a worker with shifts or events to another establishment");
        }
        // Each entry stays with the establishment it was recorded at, which is the one the worker belongs to
        if (changesEstablishment && timeEntryRepository.existsByWorker_UserId(userId)) {
            throw new BusinessRuleException("Cannot move a worker with time entries to another establishment");
        }
        Establishment target = getEstablishment(request.establishmentId());
        Long targetOwnerId = target.getOwner().getId();
        boolean joinsOwner = !worker.getActive() || !worker.getEstablishment().getOwner().getId().equals(targetOwnerId);
        if (Boolean.TRUE.equals(request.isActive()) && joinsOwner) {
            planLimits.checkRoomForOneMore(targetOwnerId);
        }
        workerMapper.updateEntity(worker, request, target);
        return workerMapper.toResponse(worker);
    }

    // Time entries are the record of the hours worked, so a worker who has any is deactivated, not deleted.
    // The worker's lock makes a clock-in sent meanwhile wait, and then find no worker.
    @Override
    public void delete(Long userId) {
        Worker worker = lockWorker(userId);
        if (timeEntryRepository.existsByWorker_UserId(userId)) {
            throw new BusinessRuleException("Cannot delete a worker who has time entries; deactivate them instead"
                    + " (PUT /api/workers/" + userId + "/active) to keep their time entries");
        }
        workerRepository.delete(worker);
    }

    @Override
    public void changeActivationStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        setActive(user.getId(), !getWorker(user.getId()).getActive().equals(true));
    }

    // Reactivating someone counts against the plan again; deactivating always works
    @Override
    public WorkerResponseDTO setActive(Long userId, boolean active) {
        Worker worker = getWorker(userId);
        if (active && !worker.getActive()) {
            planLimits.checkRoomForOneMore(worker.getEstablishment().getOwner().getId());
        }
        worker.setActive(active);
        if (!active) {
            // A deactivated worker can't sign in, and nothing should keep reaching their devices
            refreshSessionRepository.deleteByUserId(userId);
            pushSubscriptionRepository.deleteByUserId(userId);
        }
        return workerMapper.toResponse(worker);
    }

    private Worker getWorker(Long userId) {
        return workerRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker", userId));
    }

    // Holds the worker's row lock until commit, the lock their time entries and schedule changes take
    private Worker lockWorker(Long userId) {
        return workerRepository.lockById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker", userId));
    }

    private Establishment getEstablishment(Long id) {
        return establishmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Establishment", id));
    }
}
