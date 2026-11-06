package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.request.WorkerEventRequestDTO;
import com.moga.khronosdemo.dtos.response.WorkerEventResponseDTO;
import com.moga.khronosdemo.entities.Event;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.WorkerEvent;
import com.moga.khronosdemo.exceptions.BusinessRuleException;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.mappers.WorkerEventMapper;
import com.moga.khronosdemo.repositories.EventRepository;
import com.moga.khronosdemo.repositories.WorkerEventRepository;
import com.moga.khronosdemo.repositories.WorkerRepository;
import com.moga.khronosdemo.services.WorkerEventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class WorkerEventServiceImpl implements WorkerEventService {

    private final WorkerEventRepository workerEventRepository;
    private final WorkerRepository workerRepository;
    private final EventRepository eventRepository;
    private final WorkerEventMapper workerEventMapper;

    public WorkerEventServiceImpl(WorkerEventRepository workerEventRepository, WorkerRepository workerRepository,
                                  EventRepository eventRepository, WorkerEventMapper workerEventMapper) {
        this.workerEventRepository = workerEventRepository;
        this.workerRepository = workerRepository;
        this.eventRepository = eventRepository;
        this.workerEventMapper = workerEventMapper;
    }

    @Override
    public WorkerEventResponseDTO create(WorkerEventRequestDTO request) {
        Worker worker = workerRepository.findById(request.workerId())
                .orElseThrow(() -> new ResourceNotFoundException("Worker", request.workerId()));
        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event", request.eventId()));
        if (workerEventRepository.existsByWorker_UserIdAndEvent_Id(request.workerId(), request.eventId())) {
            throw new BusinessRuleException("Worker is already assigned to this event");
        }
        if (!event.getEstablishment().getId().equals(worker.getEstablishment().getId())) {
            throw new BusinessRuleException("The event does not belong to the worker's establishment");
        }
        WorkerEvent workerEvent = workerEventMapper.toEntity(request, worker, event);
        return workerEventMapper.toResponse(workerEventRepository.save(workerEvent));
    }

    @Override
    @Transactional(readOnly = true)
    public WorkerEventResponseDTO findById(Long id) {
        return workerEventMapper.toResponse(getWorkerEvent(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkerEventResponseDTO> findByWorker(Long workerId) {
        return workerEventRepository.findByWorker_UserId(workerId).stream().map(workerEventMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkerEventResponseDTO> findByEvent(Long eventId) {
        return workerEventRepository.findByEvent_Id(eventId).stream().map(workerEventMapper::toResponse).toList();
    }

    @Override
    public void delete(Long id) {
        workerEventRepository.delete(getWorkerEvent(id));
    }

    private WorkerEvent getWorkerEvent(Long id) {
        return workerEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkerEvent", id));
    }
}
