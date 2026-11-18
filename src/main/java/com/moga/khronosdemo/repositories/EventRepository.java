package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.Event;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    // The zone is fetched with the event because the response includes its name.
    // A null bound leaves that side of the range open; the casts give PostgreSQL its type.
    @Query("""
            select e from Event e join fetch e.zone z
            where z.id = :zoneId
              and (cast(:from as LocalDateTime) is null or e.dateTime >= :from)
              and (cast(:until as LocalDateTime) is null or e.dateTime < :until)
            order by e.dateTime, e.id
            """)
    List<Event> findByZone(@Param("zoneId") Long zoneId,
                           @Param("from") LocalDateTime from,
                           @Param("until") LocalDateTime until);

    @Query("""
            select e from Event e join fetch e.zone z
            where z.establishment.id = :establishmentId
              and (cast(:from as LocalDateTime) is null or e.dateTime >= :from)
              and (cast(:until as LocalDateTime) is null or e.dateTime < :until)
            order by e.dateTime, e.id
            """)
    List<Event> findByEstablishment(@Param("establishmentId") Long establishmentId,
                                    @Param("from") LocalDateTime from,
                                    @Param("until") LocalDateTime until);

    // Locks the events' rows until the transaction ends, in id order. Whatever locks events and workers
    // locks the events first and the workers after (WorkerRepository.lockAllById), so two requests never
    // end up waiting for each other.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id in :ids order by e.id")
    List<Event> lockAllById(@Param("ids") Collection<Long> ids);

    // Locks one event's row in the same way; empty when there is no such event
    default Optional<Event> lockById(Long id) {
        return lockAllById(List.of(id)).stream().findFirst();
    }
}
