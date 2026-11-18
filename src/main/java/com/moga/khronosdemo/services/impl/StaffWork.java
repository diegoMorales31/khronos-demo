package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.entities.TimeEntry;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.WorkerShift;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * An establishment's workers over a range of business days: each one's shifts and counted (not voided)
 * entries, and their hourly rates. Staff costs are worked out from it, by the hours report and by the
 * business summary, so both give the same figures. {@link StaffWorkLoader} loads it in four queries,
 * however many workers or days the range holds.
 */
final class StaffWork {

    private final ZoneId zone;
    private final List<Worker> workers;
    private final Map<Long, WorkTally> tallies = new HashMap<>();
    private final Map<Long, RateSchedule> rates;

    StaffWork(ZoneId zone, List<Worker> workers, Collection<WorkerShift> shifts, Collection<TimeEntry> entries,
              Map<Long, RateSchedule> rates) {
        this.zone = zone;
        this.workers = List.copyOf(workers);
        this.rates = rates;
        workers.forEach(worker -> tallies.put(worker.getUserId(), new WorkTally(zone)));
        // Shifts and entries belong to the establishment's current workers, because a worker with either
        // cannot move to another establishment; anything else is skipped rather than failing the report
        for (WorkerShift shift : shifts) {
            WorkTally tally = tallies.get(shift.getWorker().getUserId());
            if (tally != null) {
                tally.add(shift);
            }
        }
        for (TimeEntry entry : entries) {
            WorkTally tally = tallies.get(entry.getWorker().getUserId());
            if (tally != null) {
                tally.add(entry);
            }
        }
    }

    // The same workers and rates, with only the shifts and entries dated from 'from' to 'to', both included
    StaffWork between(LocalDate from, LocalDate to) {
        List<WorkerShift> shifts = tallies.values().stream()
                .flatMap(tally -> tally.shifts().stream())
                .filter(shift -> !shift.getDate().isBefore(from) && !shift.getDate().isAfter(to))
                .toList();
        List<TimeEntry> entries = tallies.values().stream()
                .flatMap(tally -> tally.entries().stream())
                .filter(entry -> !entry.getBusinessDate().isBefore(from) && !entry.getBusinessDate().isAfter(to))
                .toList();
        return new StaffWork(zone, workers, shifts, entries, rates);
    }

    // The workers a report counts: every active one, and inactive ones only when they have something in the range
    List<Worker> listedWorkers() {
        return workers.stream().filter(worker -> worker.getActive() || !tallyOf(worker).isEmpty()).toList();
    }

    WorkTally tallyOf(Worker worker) {
        return tallies.get(worker.getUserId());
    }

    RateSchedule ratesOf(Worker worker) {
        return rates.getOrDefault(worker.getUserId(), RateSchedule.none());
    }

    // The known planned costs of the listed workers added up; null when no worker's cost is known
    BigDecimal plannedCost() {
        return sumKnown(worker -> ratesOf(worker).costOf(tallyOf(worker).plannedMinutesByDay()));
    }

    // The known actual costs, from the hours worked, added up; null when no worker's cost is known
    BigDecimal actualCost() {
        return sumKnown(this::actualCostOf);
    }

    /**
     * The listed workers who worked in the range but whose actual cost is unknown, because they have no
     * rate for some of the days they worked: {@link #actualCost()} leaves them out. Open entries have no
     * minutes yet, so a worker who only has one is not counted.
     */
    int workersWithoutActualCost() {
        return (int) listedWorkers().stream()
                .filter(worker -> tallyOf(worker).workedMinutes() > 0 && actualCostOf(worker) == null)
                .count();
    }

    /**
     * The actual cost of each day that has worked minutes, rounded to cents per day. It counts the same
     * workers as {@link #actualCost()}, those whose cost over the whole range is known, so the days add
     * up to it give or take a rounding cent. Null when {@link #actualCost()} is null.
     */
    Map<LocalDate, BigDecimal> actualCostByDay() {
        Map<LocalDate, BigDecimal> rateTimesMinutes = new TreeMap<>();
        boolean anyKnown = false;
        for (Worker worker : listedWorkers()) {
            Map<LocalDate, BigDecimal> byDay = ratesOf(worker).rateTimesMinutesByDay(tallyOf(worker).workedMinutesByDay());
            if (byDay == null) {
                continue;
            }
            anyKnown = true;
            byDay.forEach((day, amount) -> rateTimesMinutes.merge(day, amount, BigDecimal::add));
        }
        if (!anyKnown) {
            return null;
        }
        Map<LocalDate, BigDecimal> costs = new TreeMap<>();
        rateTimesMinutes.forEach((day, amount) -> costs.put(day, RateSchedule.toEuros(amount)));
        return costs;
    }

    // Days with a shift or a counted entry, open entries included
    Set<LocalDate> activeDays() {
        Set<LocalDate> days = new TreeSet<>();
        tallies.values().forEach(tally -> {
            tally.shifts().forEach(shift -> days.add(shift.getDate()));
            tally.entries().forEach(entry -> days.add(entry.getBusinessDate()));
        });
        return days;
    }

    private BigDecimal actualCostOf(Worker worker) {
        return ratesOf(worker).costOf(tallyOf(worker).workedMinutesByDay());
    }

    private BigDecimal sumKnown(Function<Worker, BigDecimal> costOf) {
        return listedWorkers().stream().map(costOf).filter(Objects::nonNull).reduce(BigDecimal::add).orElse(null);
    }
}
