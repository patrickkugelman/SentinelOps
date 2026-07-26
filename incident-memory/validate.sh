#!/usr/bin/env bash
# Validate the curated postmortem dataset against postmortems.schema.json and
# check for duplicate ids. Uses ajv via npx (no install needed) if available,
# otherwise falls back to a structural check with node.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATASET="${HERE}/../agent/src/main/resources/incidents/postmortems.json"
SCHEMA="${HERE}/postmortems.schema.json"

echo "Dataset: ${DATASET}"
[[ -f "$DATASET" ]] || { echo "dataset not found"; exit 1; }

if command -v npx >/dev/null 2>&1 && npx --yes ajv-cli@5 help >/dev/null 2>&1; then
  echo "Validating with ajv-cli against schema ..."
  npx --yes ajv-cli@5 validate -s "$SCHEMA" -d "$DATASET" --strict=false
else
  echo "ajv-cli unavailable; running structural check with node ..."
  node -e '
    const fs=require("fs");
    const d=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
    const req=["id","title","date","affected_services","service_types","symptoms","symptom_type","error_signatures","error_pattern_category","root_cause","fix","source_url"];
    const ids=new Set(); let errs=0;
    for(const r of d){
      for(const k of req){ if(!(k in r)){console.error("MISSING",k,"in",r.id||"?");errs++;} }
      if(ids.has(r.id)){console.error("DUP id",r.id);errs++;} ids.add(r.id);
    }
    if(errs){console.error(errs+" error(s)");process.exit(1);}
    console.log("OK:",d.length,"records,",ids.size,"unique ids");
  ' "$DATASET"
fi
