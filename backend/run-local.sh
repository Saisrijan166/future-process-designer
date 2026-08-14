#!/usr/bin/env bash
#
# Starts the backend with the variables from backend/.env loaded.
#
#   ./run-local.sh          run from source, with restart-on-change
#   ./run-local.sh jar      build a jar and run that (closer to production)
#
# Spring Boot has no built-in .env support, so something has to read the file.
# A five-line script beats adding a dependency for it.

set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env ]; then
    echo "error: backend/.env not found. Copy .env.example to .env and fill it in." >&2
    exit 1
fi

# `set -a` exports everything sourced, which is what Spring needs to see them.
# Comments and blank lines in the file are handled by the shell itself.
set -a
# shellcheck disable=SC1091
source .env
set +a

if [ -z "${DATABASE_URL:-}" ]; then
    echo "error: DATABASE_URL is not set in backend/.env" >&2
    exit 1
fi

if [ -z "${GEMINI_API_KEY:-}" ]; then
    echo "warning: GEMINI_API_KEY is empty — the app will start, but Analyze will return 503." >&2
fi

echo "Database : ${DATABASE_URL}"
echo "Model    : ${GEMINI_MODEL:-gemini-2.5-flash}"
echo "CORS     : ${APP_CORS_ALLOWED_ORIGINS:-http://localhost:3000}"
echo

if [ "${1:-run}" = "jar" ]; then
    ./mvnw -B -q clean package -DskipTests
    exec java -jar target/process-designer.jar
else
    exec ./mvnw spring-boot:run
fi
