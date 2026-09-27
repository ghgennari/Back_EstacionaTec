#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
if [[ ! -f .env ]]; then
    echo 'Crie o .env a partir de .env.example e preencha as credenciais.' >&2
    exit 1
fi

docker compose config --quiet
# Finaliza os builds antes de substituir os containers em execucao.
docker compose build --pull
docker compose up -d --wait --wait-timeout 180
docker compose ps
