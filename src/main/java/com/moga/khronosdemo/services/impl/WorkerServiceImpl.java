package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.request.WorkerRequestDTO;
import com.moga.khronosdemo.dtos.response.WorkerResponseDTO;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.Role;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.exceptions.BusinessRuleException;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.mappers.WorkerMapper;
import com.moga.khronosdemo.repositories.EstablishmentRepository;
import com.moga.khronosdemo.repositories.UserRepository;
import com.moga.khronosdemo.repositories.WorkerRepository;
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

    public WorkerServiceImpl(WorkerRepository workerRepository, UserRepository userRepository,
                             EstablishmentRepository establishmentRepository, WorkerMapper workerMapper) {
        this.workerRepository = workerRepository;
        this.userRepository = userRepository;
        this.establishmentRepository = establishmentRepository;
        this.workerMapper = workerMapper;
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
        Worker worker = workerMapper.toEntity(request, user, getEstablishment(request.establishmentId()));
        return workerMapper.toResponse(workerRepository.save(worker));
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

    @Override
    public WorkerResponseDTO update(Long userId, WorkerRequestDTO request) {
        Worker worker = getWorker(userId);
        if (!userId.equals(request.userId())) {
            throw new BusinessRuleException("The user of a worker cannot be changed");
        }
        boolean changesEstablishment = !worker.getEstablishment().getId().equals(request.establishmentId());
        if (changesEstablishment && (!worker.getWorkerShifts().isEmpty() || !worker.getWorkerEvents().isEmpty())) {
            throw new BusinessRuleException("Cannot move a worker with shifts or events to another establishment");
        }
        workerMapper.updateEntity(worker, request, getEstablishment(request.establishmentId()));
        return workerMapper.toResponse(worker);
    }

    @Override
    public void delete(Long userId) {
        workerRepository.delete(getWorker(userId));
    }

    private Worker getWorker(Long userId) {
        return workerRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker", userId));
    }

    private Establishment getEstablishment(Long id) {
        return establishmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Establishment", id));
    }
}
