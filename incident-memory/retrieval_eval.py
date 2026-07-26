#!/usr/bin/env python3
"""
Retrieval-quality evaluation harness for SentinelOps' incident memory.

Hits the agent's live `/api/incidents/retrieve` endpoint with a benchmark set
of incident signatures and scores the hybrid (vector + structured) retriever
against the "same kind of incident" property the design relies on: e.g. a
network/latency signature should surface network/latency postmortems, not
crashes, and a CPU-saturation query should surface resource-exhaustion ones.

This complements (does not replace) the Java unit test
`IncidentRetrievalQualityTest` — that one runs offline against the in-process
ranker; this one is an external, black-box check against the deployed service,
the kind of QA/eval tooling a data/ML engineer would run against a live system.

Usage:
    pip install -r requirements.txt
    python retrieval_eval.py                      # against http://localhost:8090
    python retrieval_eval.py --agent-url http://localhost:8090 --top-k 5
    python retrieval_eval.py --json-report report.json
    python retrieval_eval.py --min-precision-at-3 0.34   # CI gate threshold
"""
from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass, field
from typing import Any

import requests

# ---------------------------------------------------------------------------
# Benchmark: (name, signature, expected_symptom_type, optional expected id
# that should appear near the top — the canonical precedent for that kind of
# incident, per the curated postmortem dataset).
# ---------------------------------------------------------------------------


@dataclass
class BenchmarkQuery:
    name: str
    signature: dict[str, Any]
    expected_symptom_type: str
    expected_top_id: str | None = None


BENCHMARK: list[BenchmarkQuery] = [
    BenchmarkQuery(
        name="network-delay",
        signature={
            "serviceTypes": ["network", "proxy"],
            "symptomType": "latency",
            "errorPatternCategory": "network-latency",
            "description": "downstream calls slow, high latency and timeouts, network delay",
        },
        expected_symptom_type="latency",
    ),
    BenchmarkQuery(
        name="cpu-saturation",
        signature={
            "serviceTypes": ["proxy", "compute"],
            "symptomType": "resource-exhaustion",
            "errorPatternCategory": "cpu-saturation",
            "description": "CPU pinned at 100 percent, workers unresponsive",
        },
        expected_symptom_type="resource-exhaustion",
        expected_top_id="cloudflare-2019-07-02-waf-regex",
    ),
    BenchmarkQuery(
        name="pod-crash",
        signature={
            "serviceTypes": ["compute"],
            "symptomType": "crash",
            "errorPatternCategory": "process-crash",
            "description": "pod killed, CrashLoopBackOff, container restarting repeatedly",
        },
        expected_symptom_type="crash",
    ),
    BenchmarkQuery(
        name="network-partition",
        signature={
            "serviceTypes": ["network"],
            "symptomType": "error-rate",
            "errorPatternCategory": "network-partition",
            "description": "service unreachable, connection refused, network split between data centers",
        },
        expected_symptom_type="error-rate",
    ),
    BenchmarkQuery(
        name="data-deletion",
        signature={
            "serviceTypes": ["database"],
            "symptomType": "data-loss",
            "errorPatternCategory": "data-deletion",
            "description": "wrong directory deleted, database data permanently lost, backups failed",
        },
        expected_symptom_type="data-loss",
    ),
    BenchmarkQuery(
        name="bgp-misconfiguration",
        signature={
            "serviceTypes": ["network", "cdn"],
            "symptomType": "error-rate",
            "errorPatternCategory": "bgp-misconfiguration",
            "description": "BGP routes withdrawn, global unreachability after a network config change",
        },
        expected_symptom_type="error-rate",
    ),
    BenchmarkQuery(
        name="schema-migration",
        signature={
            "serviceTypes": ["database", "api"],
            "symptomType": "error-rate",
            "errorPatternCategory": "schema-migration",
            "description": "database migration broke queries, type mismatch on read after deploy",
        },
        expected_symptom_type="error-rate",
    ),
    BenchmarkQuery(
        name="expired-certificate",
        signature={
            "serviceTypes": ["security"],
            "symptomType": "error-rate",
            "errorPatternCategory": "expired-certificate",
            "description": "certificate expired, signature verification failed, service disabled",
        },
        expected_symptom_type="error-rate",
    ),
]


@dataclass
class QueryResult:
    query: BenchmarkQuery
    top_ids: list[str] = field(default_factory=list)
    top_symptom_types: list[str] = field(default_factory=list)
    precision_at_1: float = 0.0
    precision_at_3: float = 0.0
    first_correct_rank: int | None = None  # 1-indexed, for MRR
    expected_id_rank: int | None = None


def retrieve(agent_url: str, signature: dict[str, Any], top_k: int) -> list[dict[str, Any]]:
    resp = requests.post(
        f"{agent_url}/api/incidents/retrieve",
        params={"topK": top_k},
        json=signature,
        timeout=10,
    )
    resp.raise_for_status()
    return resp.json()


def evaluate_query(agent_url: str, q: BenchmarkQuery, top_k: int) -> QueryResult:
    scored = retrieve(agent_url, q.signature, top_k)
    result = QueryResult(query=q)
    result.top_ids = [s["incident"]["id"] for s in scored]
    result.top_symptom_types = [s["incident"]["symptom_type"] for s in scored]

    matches = [t == q.expected_symptom_type for t in result.top_symptom_types]
    if matches:
        result.precision_at_1 = 1.0 if matches[0] else 0.0
        top3 = matches[:3]
        result.precision_at_3 = sum(top3) / len(top3) if top3 else 0.0
        for i, m in enumerate(matches, start=1):
            if m:
                result.first_correct_rank = i
                break

    if q.expected_top_id and q.expected_top_id in result.top_ids:
        result.expected_id_rank = result.top_ids.index(q.expected_top_id) + 1

    return result


def run(agent_url: str, top_k: int) -> list[QueryResult]:
    return [evaluate_query(agent_url, q, top_k) for q in BENCHMARK]


def print_report(results: list[QueryResult]) -> None:
    print(f"\n{'query':<22} {'expected':<20} {'p@1':>5} {'p@3':>5}  top result")
    print("-" * 90)
    for r in results:
        top = f"{r.top_ids[0]} ({r.top_symptom_types[0]})" if r.top_ids else "(no results)"
        print(f"{r.query.name:<22} {r.query.expected_symptom_type:<20} "
              f"{r.precision_at_1:>5.2f} {r.precision_at_3:>5.2f}  {top}")
        if r.query.expected_top_id:
            status = f"rank {r.expected_id_rank}" if r.expected_id_rank else "NOT in top-k"
            marker = "OK" if r.expected_id_rank else "MISS"
            print(f"{'':<22} expected top id: {r.query.expected_top_id} -> {status} [{marker}]")

    mean_p1 = sum(r.precision_at_1 for r in results) / len(results)
    mean_p3 = sum(r.precision_at_3 for r in results) / len(results)
    reciprocal_ranks = [1.0 / r.first_correct_rank for r in results if r.first_correct_rank]
    mrr = sum(reciprocal_ranks) / len(results) if results else 0.0

    print("-" * 90)
    print(f"mean precision@1: {mean_p1:.3f}")
    print(f"mean precision@3: {mean_p3:.3f}")
    print(f"MRR:              {mrr:.3f}")


def to_json(results: list[QueryResult]) -> dict[str, Any]:
    return {
        "queries": [
            {
                "name": r.query.name,
                "expected_symptom_type": r.query.expected_symptom_type,
                "top_ids": r.top_ids,
                "top_symptom_types": r.top_symptom_types,
                "precision_at_1": r.precision_at_1,
                "precision_at_3": r.precision_at_3,
                "first_correct_rank": r.first_correct_rank,
                "expected_top_id": r.query.expected_top_id,
                "expected_top_id_rank": r.expected_id_rank,
            }
            for r in results
        ],
        "mean_precision_at_1": sum(r.precision_at_1 for r in results) / len(results),
        "mean_precision_at_3": sum(r.precision_at_3 for r in results) / len(results),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--agent-url", default="http://localhost:8090", help="Base URL of the running agent")
    parser.add_argument("--top-k", type=int, default=5, help="How many precedents to request per query")
    parser.add_argument("--json-report", help="Optional path to write a JSON report to")
    parser.add_argument("--min-precision-at-3", type=float, default=0.0,
                         help="Exit non-zero if mean precision@3 falls below this (for CI gating)")
    args = parser.parse_args()

    try:
        results = run(args.agent_url, args.top_k)
    except requests.exceptions.ConnectionError:
        print(f"error: could not reach the agent at {args.agent_url} — "
              f"start it first (cd agent && mvn spring-boot:run)", file=sys.stderr)
        return 2
    except requests.exceptions.HTTPError as e:
        print(f"error: agent returned {e.response.status_code}: {e.response.text}", file=sys.stderr)
        return 2

    print_report(results)

    if args.json_report:
        with open(args.json_report, "w") as f:
            json.dump(to_json(results), f, indent=2)
        print(f"\nwrote {args.json_report}")

    mean_p3 = sum(r.precision_at_3 for r in results) / len(results)
    if mean_p3 < args.min_precision_at_3:
        print(f"\nFAIL: mean precision@3 {mean_p3:.3f} < threshold {args.min_precision_at_3}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
