package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.response.ErrorCode;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.enums.Plan;
import com.moga.khronosdemo.exceptions.BusinessRuleException;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.repositories.UserRepository;
import com.moga.khronosdemo.repositories.WorkerRepository;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * The limit each plan puts on an owner's active workers, counted across all their establishments.
 * Every change that can add an active worker, and every change of plan, locks the owner's row first,
 * so two of them sent at once take turns and cannot both fit under the limit.
 * Nothing else locks users, and a worker's lock is only ever taken before this one, never after.
 * Callers run it inside their own transaction, which holds the lock until it commits.
 */
@Component
class PlanLimits {

    private final UserRepository userRepository;
    private final WorkerRepository workerRepository;

    PlanLimits(UserRepository userRepository, WorkerRepository workerRepository) {
        this.userRepository = userRepository;
        this.workerRepository = workerRepository;
    }

    // Refuses with 409 WORKER_LIMIT_REACHED when one more active worker would not fit the owner's plan
    void checkRoomForOneMore(Long ownerId) {
        User owner = lockOwner(ownerId);
        long active = workerRepository.countActiveByOwner(ownerId);
        Plan plan = owner.getPlan();
        if (!plan.allowsActiveWorkers(active + 1)) {
            throw new BusinessRuleException("The " + plan + " plan allows up to " + plan.getMaxActiveWorkers()
                    + " active workers; deactivate someone or move to a bigger plan first",
                    ErrorCode.WORKER_LIMIT_REACHED, details(plan, active));
        }
    }

    // Refuses with 409 WORKER_LIMIT_REACHED when the owner has more active workers than the new plan allows
    void checkFits(Long ownerId, Plan plan) {
        lockOwner(ownerId);
        long active = workerRepository.countActiveByOwner(ownerId);
        if (!plan.allowsActiveWorkers(active)) {
            throw new BusinessRuleException("The owner has " + active + " active workers and the " + plan
                    + " plan allows up to " + plan.getMaxActiveWorkers() + "; deactivate some first",
                    ErrorCode.WORKER_LIMIT_REACHED, details(plan, active));
        }
    }

    private User lockOwner(Long ownerId) {
        return userRepository.lockById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", ownerId));
    }

    private static Map<String, String> details(Plan plan, long active) {
        return Map.of("plan", plan.name(),
                "maxActiveWorkers", String.valueOf(plan.getMaxActiveWorkers()),
                "activeWorkers", String.valueOf(active));
    }
}
