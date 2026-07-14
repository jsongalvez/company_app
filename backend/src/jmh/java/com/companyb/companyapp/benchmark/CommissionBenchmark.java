package com.companyb.companyapp.benchmark;

import com.companyb.companyapp.service.CommissionEngineService;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.math.BigDecimal;

@State(Scope.Thread)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class CommissionBenchmark {

    private static final BigDecimal AMOUNT = new BigDecimal("150.00");
    private static final int QUANTITY = 3;

    @Benchmark
    public void splitBetweenOne(Blackhole bh) {
        bh.consume(CommissionEngineService.INSTANCE.splitCommission(AMOUNT, QUANTITY, 1));
    }

    @Benchmark
    public void splitBetweenTwo(Blackhole bh) {
        bh.consume(CommissionEngineService.INSTANCE.splitCommission(AMOUNT, QUANTITY, 2));
    }

    @Benchmark
    public void splitBetweenThree(Blackhole bh) {
        bh.consume(CommissionEngineService.INSTANCE.splitCommission(AMOUNT, QUANTITY, 3));
    }

    @Benchmark
    public void splitBetweenTen(Blackhole bh) {
        bh.consume(CommissionEngineService.INSTANCE.splitCommission(AMOUNT, QUANTITY, 10));
    }
}
