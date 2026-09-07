package com.companyb.companyapp.benchmark;

import com.companyb.companyapp.contracts.branch.BranchType;
import com.companyb.companyapp.contracts.session.SessionType;
import com.companyb.companyapp.session.SessionService;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Thread)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class SessionTypeBenchmark {

    @Benchmark
    public void computeMedicalMission(Blackhole bh) {
        bh.consume(SessionService.INSTANCE.computeSessionType(BranchType.MEDICAL_MISSION, 0L));
    }

    @Benchmark
    public void computeProvincialFirst(Blackhole bh) {
        bh.consume(SessionService.INSTANCE.computeSessionType(BranchType.PROVINCIAL_TOUR, 0L));
    }

    @Benchmark
    public void computeSecondSession(Blackhole bh) {
        bh.consume(SessionService.INSTANCE.computeSessionType(BranchType.CLINIC, 1L));
    }

    @Benchmark
    public void computeSubsequent(Blackhole bh) {
        bh.consume(SessionService.INSTANCE.computeSessionType(BranchType.CLINIC, 5L));
    }
}
