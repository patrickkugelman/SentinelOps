# SentinelOps — convenience targets. Thin wrappers over scripts/.
# Run inside WSL2. `make help` lists everything.
.DEFAULT_GOAL := help
SHELL := /usr/bin/env bash

.PHONY: help preflight up infra cluster down wipe ps psql

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
	  | awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

preflight: ## Check host tooling (docker, kind, kubectl, helm)
	@./scripts/preflight.sh

up: ## Bring up everything implemented so far
	@./scripts/setup.sh

infra: ## Only start Postgres + pgvector
	@./scripts/setup.sh --infra

cluster: ## Only create the kind cluster
	@./scripts/setup.sh --cluster

down: ## Delete cluster, stop Postgres (keep data)
	@./scripts/teardown.sh

wipe: ## Tear down and delete the Postgres data volume
	@./scripts/teardown.sh --wipe

ps: ## Show infra + cluster status
	@docker compose ps || true
	@kubectl get pods -A 2>/dev/null || true

psql: ## Open a psql shell into the incident-memory DB
	@docker exec -it sentinelops-postgres psql -U $${POSTGRES_USER:-sentinelops} -d $${POSTGRES_DB:-sentinelops}
