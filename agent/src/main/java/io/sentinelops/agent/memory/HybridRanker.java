package io.sentinelops.agent.memory;

import io.sentinelops.agent.memory.RetrievalProperties.Weights;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Re-ranks vector-recalled candidates by combining cosine similarity with
 * structured-signature signals (symptom type, error-pattern category, and
 * service-type overlap). This is what makes retrieval reflect "the same KIND of
 * incident" rather than just similar wording. Pure and unit-testable.
 */
public class HybridRanker {

    private final Weights w;

    public HybridRanker(Weights weights) {
        this.w = weights;
    }

    public List<ScoredIncident> rank(IncidentSignature query, List<Candidate> candidates) {
        return candidates.stream()
                .map(c -> score(query, c))
                .sorted(Comparator.comparingDouble(ScoredIncident::score).reversed())
                .toList();
    }

    private ScoredIncident score(IncidentSignature q, Candidate c) {
        Incident inc = c.incident();

        double vec = clamp01(c.vectorSimilarity());
        double symptom = equalsIgnoreCase(q.symptomType(), inc.symptomType()) ? 1.0 : 0.0;
        double errCat = equalsIgnoreCase(q.errorPatternCategory(), inc.errorPatternCategory()) ? 1.0 : 0.0;
        double service = jaccard(q.serviceTypes(), inc.serviceTypes());

        double total = w.vector() * vec
                + w.symptom() * symptom
                + w.errorCategory() * errCat
                + w.service() * service;

        return new ScoredIncident(inc, total, vec, symptom, errCat, service);
    }

    private static double jaccard(List<String> a, List<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        Set<String> sa = lower(a);
        Set<String> sb = lower(b);
        Set<String> inter = new HashSet<>(sa);
        inter.retainAll(sb);
        Set<String> union = new HashSet<>(sa);
        union.addAll(sb);
        return union.isEmpty() ? 0.0 : (double) inter.size() / union.size();
    }

    private static Set<String> lower(List<String> xs) {
        return xs.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private static double clamp01(double x) {
        return x < 0 ? 0 : Math.min(x, 1);
    }
}
