package io.sentinelops.agent.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

/** On startup, seed the incident memory from the curated dataset if it's empty. */
@Component
public class IncidentAutoIngest implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IncidentAutoIngest.class);

    private final IncidentMemoryService memory;
    private final boolean enabled;

    public IncidentAutoIngest(IncidentMemoryService memory,
                              @Value("${sentinelops.incidents.auto-ingest:true}") boolean enabled) {
        this.memory = memory;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        try {
            if (memory.count() == 0) {
                int n = memory.ingestDataset();
                log.info("Auto-ingest complete: {} incidents in memory", n);
            } else {
                log.info("Incident memory already populated ({} rows) — skipping auto-ingest", memory.count());
            }
        } catch (Exception e) {
            // Don't block startup on ingest problems (e.g. DB not ready yet).
            log.warn("Auto-ingest skipped: {}", e.toString());
        }
    }
}
