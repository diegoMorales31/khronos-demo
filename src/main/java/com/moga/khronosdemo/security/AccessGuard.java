package com.moga.khronosdemo.security;

import com.moga.khronosdemo.repositories.EstablishmentRepository;
import com.moga.khronosdemo.repositories.EventRepository;
import com.moga.khronosdemo.repositories.WorkerEventRepository;
import com.moga.khronosdemo.repositories.WorkerRepository;
import com.moga.khronosdemo.repositories.WorkerShiftRepository;
import com.moga.khronosdemo.repositories.ZoneRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

@Component("access")
@Transactional(readOnly = true)
public class AccessGuard {

    private final EstablishmentRepository establishmentRepository;
    private final ZoneRepository zoneRepository;
    private final EventRepository eventRepository;
    private final WorkerRepository workerRepository;
    private final WorkerShiftRepository workerShiftRepository;
    private final WorkerEventRepository workerEventRepository;

    public AccessGuard(EstablishmentRepository establishmentRepository, ZoneRepository zoneRepository,
                       EventRepository eventRepository, WorkerRepository workerRepository,
                       WorkerShiftRepository workerShiftRepository, WorkerEventRepository workerEventRepository) {
        this.establishmentRepository = establishmentRepository;
        this.zoneRepository = zoneRepository;
        this.eventRepository = eventRepository;
        this.workerRepository = workerRepository;
        this.workerShiftRepository = workerShiftRepository;
        this.workerEventRepository = workerEventRepository;
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
}
