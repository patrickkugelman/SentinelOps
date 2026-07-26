package io.sentinelops.agent.web;

import io.sentinelops.agent.memory.Incident;
import io.sentinelops.agent.memory.IncidentMemoryService;
import io.sentinelops.agent.memory.IncidentSignature;
import io.sentinelops.agent.memory.ScoredIncident;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Incident-memory API: ingest, retrieve precedents, and record outcomes. */
@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentMemoryService memory;

    public IncidentController(IncidentMemoryService memory) {
        this.memory = memory;
    }

    @GetMapping("/count")
    public Map<String, Long> count() {
        return Map.of("count", memory.count());
    }

    @PostMapping("/ingest")
    public Map<String, Integer> ingest() {
        return Map.of("ingested", memory.ingestDataset());
    }

    /** Retrieve top-k precedents for a structured signature (hybrid ranking). */
    @PostMapping("/retrieve")
    public List<ScoredIncident> retrieve(@RequestBody IncidentSignature signature,
                                         @RequestParam(defaultValue = "5") int topK) {
        return memory.retrieve(signature, topK);
    }

    /** Write-back: record a resolved incident into the same store. */
    @PostMapping("/record")
    public Map<String, String> record(@Valid @RequestBody Incident incident) {
        memory.record(incident);
        return Map.of("recorded", incident.id());
    }
}
