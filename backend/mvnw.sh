#!/usr/bin/env bash
# Sestavení a testy backendu v kontejneru Mavenu (lokálně mvn není instalováno).
# Docker socket je potřeba pro integrační testy (Testcontainers).
#
#   ./backend/mvnw.sh test          # unit testy (rychlé, bez kontejneru)
#   ./backend/mvnw.sh verify        # unit + integrační testy (Testcontainers/Postgres)
#   ./backend/mvnw.sh -DskipTests package
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec docker run --rm \
  --network host \
  -v "$ROOT":/app \
  -v bazar-m2:/root/.m2 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_RYUK_DISABLED=true \
  -e TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  -e DOCKER_API_VERSION=1.44 \
  -w /app \
  maven:3.9-eclipse-temurin-17 \
  mvn -B "$@"