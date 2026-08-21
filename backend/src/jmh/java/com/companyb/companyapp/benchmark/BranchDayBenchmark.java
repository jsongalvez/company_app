package com.companyb.companyapp.benchmark;

import com.companyb.companyapp.domain.DayStatus;
import com.companyb.companyapp.service.branchday.BranchDayService;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.time.LocalDate;

@State(Scope.Thread)
@Fork(2)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 2)
public class BranchDayBenchmark {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 14);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);

    @Benchmark
    public void evaluateOpenFuture(Blackhole bh) {
        bh.consume(BranchDayService.INSTANCE.evaluateStatus(DayStatus.OPEN, TOMORROW, TODAY));
    }

    @Benchmark
    public void evaluateOpenPast(Blackhole bh) {
        bh.consume(BranchDayService.INSTANCE.evaluateStatus(DayStatus.OPEN, YESTERDAY, TODAY));
    }

    @Benchmark
    public void evaluateRemitted(Blackhole bh) {
        bh.consume(BranchDayService.INSTANCE.evaluateStatus(DayStatus.REMITTED, YESTERDAY, TODAY));
    }

    @Benchmark
    public void expirationUtc(Blackhole bh) {
        bh.consume(BranchDayService.INSTANCE.expirationUtc(TODAY));
    }
}
