package com.evcharging.platform;

import com.evcharging.platform.station.Station;
import com.evcharging.platform.station.StationQueryService;
import com.evcharging.platform.station.StationRepository;
import com.evcharging.platform.station.StationService;
import com.evcharging.platform.station.StationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CacheBenchmarkTest {

    private static final int WARMUP = 300;
    private static final int RUNS = 1000;

    @Autowired StationRepository stationRepository;
    @Autowired StationQueryService queryService;
    @Autowired StationService stationService;
    @Autowired CacheManager cacheManager;

    @Test
    void cachedReadsVersusDatabaseReadsAndEviction() throws Exception {
        UUID id = stationRepository.save(new Station("Bench Station", "1 Bench St")).getId();

        for (int i = 0; i < WARMUP; i++) {
            queryService.getUncached(id);
            queryService.getCached(id);
        }

        long[] uncached = time(() -> queryService.getUncached(id));
        long[] cached = time(() -> queryService.getCached(id));
        double meanUncached = mean(uncached);
        double meanCached = mean(cached);

        System.out.printf("BENCH uncached(Postgres): mean=%.3f ms  p50=%.3f ms  p95=%.3f ms%n",
                meanUncached, percentile(uncached, 50), percentile(uncached, 95));
        System.out.printf("BENCH cached(Redis):      mean=%.3f ms  p50=%.3f ms  p95=%.3f ms%n",
                meanCached, percentile(cached, 50), percentile(cached, 95));
        System.out.printf("BENCH mean latency reduction: %.1f%%%n", (1 - meanCached / meanUncached) * 100);

        assertEquals(StationStatus.ACTIVE, queryService.getCached(id).status());
        Cache cache = cacheManager.getCache("station");
        assertNotNull(cache, "The 'station' cache must exist");

        stationService.deactivate(id);

        // Eviction is allowed up to 1 second to land; report how long it took.
        long start = System.nanoTime();
        while (cache.get(id) != null && (System.nanoTime() - start) < 1_000_000_000L) {
            Thread.sleep(1);
        }
        System.out.printf("EVICTION visible after %.1f ms%n", (System.nanoTime() - start) / 1e6);
        assertNull(cache.get(id), "Station cache entry must be evicted within 1 second");
        assertEquals(StationStatus.INACTIVE, queryService.getCached(id).status());
    }

    private long[] time(Runnable op) {
        long[] nanos = new long[RUNS];
        for (int i = 0; i < RUNS; i++) {
            long s = System.nanoTime();
            op.run();
            nanos[i] = System.nanoTime() - s;
        }
        return nanos;
    }

    private double mean(long[] n) {
        return Arrays.stream(n).average().orElse(0) / 1_000_000.0;
    }

    private double percentile(long[] n, int p) {
        long[] s = n.clone();
        Arrays.sort(s);
        int i = Math.max(0, Math.min((int) Math.ceil(p / 100.0 * s.length) - 1, s.length - 1));
        return s[i] / 1_000_000.0;
    }
}
