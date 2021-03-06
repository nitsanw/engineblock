/*
 *
 *    Copyright 2016 jshook
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 * /
 */

package io.engineblock.activityapi.rates;

import com.codahale.metrics.Gauge;
import io.engineblock.activityapi.core.Startable;
import io.engineblock.activityimpl.ActivityDef;
import io.engineblock.metrics.ActivityMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * <p>This rate limiter uses nanoseconds as the unit of timing. This
 * works well because it is the native precision of the system timer
 * interface via {@link System#nanoTime()}. It is also low-error
 * in terms of rounding between floating point rates and nanoseconds,
 * at least in the round numbers that users tend to use. Further,
 * the current scheduling state is maintained as an atomic view of
 * accumulated nanoseconds granted to callers -- referred to here as the
 * ticks accumulator. This further simplifies the implementation by
 * allowing direct comparison of scheduled times with the current
 * state of the high-resolution system timer.
 *
 * <p>
 * Each time {@link #acquire()} or {@link #acquire(long)} is called,
 * a discrete scheduled time is calculated from the current state of
 * the ticks accumulator. If the calculated time is in the future,
 * then the method blocks (in the calling thread) using
 * {@link Thread#sleep(long, int)}. Finally, the method is unblocked,
 * and the nanosecond scheduling gap is returned to the caller.
 *
 * <p>
 * This implementation of the rate limiter is simplified to only support
 * averaging rate limits. The {@link RateLimiters} class provides a
 * convenient way for using either the averaging rate limiter or the
 * strict rate limiter at runtime, while allowing the averaging rate
 * limiter to be used when desired for higher client-side throughput.
 *
 * <p>
 * Note that the ticks accumulator can not rate limit a single event.
 * Acquiring a grant at some nanosecond size simply consumes nanoseconds
 * from the schedule, with the start time of the allotted time span
 * being conceptually aligned with the start time of the requested event.
 * In other words, previous allocations of the timeline determine the start
 * time of a subsequent caller, not the caller itself.
 */
public class AverageRateLimiter implements Startable, RateLimiter {
    private static final Logger logger = LoggerFactory.getLogger(AverageRateLimiter.class);
    private String label;
    private ActivityDef activityDef;
    private RateSpec rateSpec;
    private long opTicks = 0L; // Number of nanos representing one grant at target rate
    protected final AtomicLong ticksTimeline = new AtomicLong(0L);
    private final AtomicLong accumulatedDelayNanos = new AtomicLong(0L);
    
    private State state = State.Idle;
    private boolean reportCoDelay = false;

    //private Counter fastpathCounter;
    //private Counter sleepCounter;
    private Gauge<Long> delayGauge;
    private Gauge<Double> avgRateGauge;
    //private Gauge<Double> burstRateGauge;

    protected AverageRateLimiter() {
    }

    /**
     * Create a rate limiter.
     *
     * @param def The activity definition for this rate limiter
     * @param label The label for the rate limiting facet within the activity
     * @param rateSpec the rate limiter configuration
     */
    public AverageRateLimiter(ActivityDef def, String label, RateSpec rateSpec) {
        setActivityDef(def);
        setLabel(label);
        this.setRateSpec(rateSpec);
        init();
    }

    protected void setLabel(String label) {
        this.label = label;
    }

    protected void setActivityDef(ActivityDef def) {
        this.activityDef = def;
    }

    protected void init() {
        this.delayGauge = ActivityMetrics.gauge(activityDef, label+".cco_delay_gauge", new RateLimiters.DelayGauge(this));
        //this.sleepCounter = ActivityMetrics.counter(activityDef, label + "_ratelogic.sleep_counter");
        //this.fastpathCounter = ActivityMetrics.counter(activityDef, label + "_ratelogic.fast_counter");
        this.avgRateGauge = ActivityMetrics.gauge(activityDef, label + ".avg_targetrate_gauge", new RateLimiters.RateGauge(this));
        //this.burstRateGauge = ActivityMetrics.gauge(activityDef, label + "_ratelogic.burst_targetrate_gauge", new RateLimiters.BurstRateGauge(this));
        start();
    }

    protected long getNanoClockTime() {
        return System.nanoTime();
    }

    /**
     * See {@link AverageRateLimiter} for interface docs.
     * effective calling overhead of acquire() is ~20ns
     *
     * @param nanos nanoseconds of time allotted to this event
     * @return nanoseconds that have already elapsed since this event's ideal time
     */
    @Override
    public long acquire(long nanos) {

        long opScheduleTimeNs = ticksTimeline.getAndAdd(nanos);
        long delayNs = opScheduleTimeNs - getNanoClockTime();
        // event is in the past, return immediately
        if (delayNs <= 0) {
            return reportCoDelay ? -delayNs : 0L;
        }

        do {
            LockSupport.parkNanos(delayNs);
            // threads can spuriously unpark, must retest on wakeup.
            delayNs = opScheduleTimeNs - getNanoClockTime();
        } while (delayNs > 0);
        
        return reportCoDelay ? -delayNs : 0L;
    }

    @Override
    public long acquire() {
        return acquire(opTicks);
    }

    public long getOpNanos() {
        return opTicks;
    }

    @Override
    public double getRate() {
        return rateSpec.opsPerSec;
    }

    @Override
    public synchronized void setRate(double rate) {
        setRateSpec(this.rateSpec.withOpsPerSecond(rate));
    }

    @Override
    public long getTotalSchedulingDelay() {
        return getRateSchedulingDelay() + accumulatedDelayNanos.get();
    }

    protected long getRateSchedulingDelay() {
        return getNanoClockTime() - ticksTimeline.get();
    }


    public synchronized void start() {
        switch (state) {
            case Started:
                break;
            case Idle:
                sync();
                state = State.Started;
                break;
        }
    }

    private synchronized void sync() {
        switch (this.state) {
            case Idle:
                this.ticksTimeline.set(getNanoClockTime());
                this.accumulatedDelayNanos.set(0L);
                break;
            case Started:
                accumulatedDelayNanos.addAndGet(getRateSchedulingDelay());
                break;
        }
    }

    public String toString() {
        return "spec=[" + label + "]:" + rateSpec.toString() +
                ", delay=" + this.getRateSchedulingDelay() +
                ", total=" + this.getTotalSchedulingDelay() +
                ", ticksTimeline=" + ticksTimeline.get() +
                ", (clock,actual)=(" + getNanoClockTime() + "," + System.nanoTime() + ")";
    }

    @Override
    public RateSpec getRateSpec() {
        return this.rateSpec;
    }

    @Override
    public void setRateSpec(RateSpec updatingRateSpec) {
        RateSpec oldRateSpec = this.rateSpec;
        this.rateSpec=updatingRateSpec;

        if (oldRateSpec!=null && oldRateSpec.equals(this.rateSpec)) {
            return;
        }

        this.reportCoDelay = updatingRateSpec.getReportCoDelay();
        this.opTicks = updatingRateSpec.getCalculatedNanos();
        this.rateSpec = updatingRateSpec;
        switch (this.state) {
            case Started:
                sync();
            case Idle:
        }
    }

    /**
     * * visible for testing
     *
     * @return ticksTimeline value - the long value of the shared timeslice marker
     */
    public AtomicLong getTicksTimeline() {
        return this.ticksTimeline;
    }

    private enum State {
        Idle,
        Started
    }
}
