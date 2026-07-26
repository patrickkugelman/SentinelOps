package io.sentinelops.agent.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Loads the curated postmortem dataset from the classpath. */
@Component
public class IncidentDatasetLoader {

    private static final String DATASET = "incidents/postmortems.json";

    private final ObjectMapper mapper;

    public IncidentDatasetLoader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<Incident> load() {
        try (InputStream is = new ClassPathResource(DATASET).getInputStream()) {
            return mapper.readValue(is, mapper.getTypeFactory()
                    .constructCollectionType(List.class, Incident.class));
        } catch (IOException e) {
            throw new IllegalStateException("failed to load incident dataset " + DATASET, e);
        }
    }
}
