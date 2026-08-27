#!/usr/bin/env bash
#
# Starts the backend with the variables from backend/.env loaded.
#
#   ./run-local.sh          run from source, with restart-on-change
#   ./run-local.sh jar      build a jar and run that (closer to production)
#
# The app also imports backend/.env by itself (see application.yml), so a plain
# `./mvnw spring-boot:run` works too. This script stays because it fails loudly
# on a missing file and prints what it is about to connect to, which is worth
# having when a run does not do what you expected.

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

if [ -z "${GROQ_API_KEY:-}" ] && [ -z "${GEMINI_API_KEY:-}" ]; then
    echo "warning: no provider key set — the app will start, but Analyze will return 503." >&2
elif [ -z "${GROQ_API_KEY:-}" ]; then
    echo "warning: GROQ_API_KEY is empty — every stage falls through to Gemini, which is slower" >&2
    echo "         and has a much smaller free allowance." >&2
elif [ -z "${GEMINI_API_KEY:-}" ]; then
    echo "note: GEMINI_API_KEY is empty. Runs still work, but the high-volume stages cannot" >&2
    echo "      alternate into a second provider, so expect a slower analysis." >&2
fi

echo "Database : ${DATABASE_URL}"
echo "Provider : ${AI_PROVIDER:-groq} -> ${GROQ_MODEL:-openai/gpt-oss-120b}"
echo "Fallback : ${AI_FALLBACK_PROVIDERS:-gemini}"
echo "Pipeline : ${ANALYSIS_PIPELINE:-staged}, research ${RESEARCH_ENABLED:-true}"
echo "CORS     : ${APP_CORS_ALLOWED_ORIGINS:-http://localhost:3000}"
echo

if [ "${1:-run}" = "jar" ]; then
    ./mvnw -B -q clean package -DskipTests
    exec java -jar target/process-designer.jar
else
    exec ./mvnw spring-boot:run
fi
