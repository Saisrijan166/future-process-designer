#!/usr/bin/env bash
#
# Manages a local PostgreSQL for development, without Docker and without root.
#
#   ./scripts/local-db.sh start|stop|status|psql|reset
#
# Why this exists: the machine this was built on has a system PostgreSQL running
# that the developer account cannot authenticate against, and no Docker daemon
# access. `initdb` needs neither — it creates a cluster owned by the current user
# in the current user's home directory, on a non-default port so it cannot collide
# with the system instance.
#
# If you do have Docker, `docker compose up -d` is the simpler route; point
# DATABASE_URL at localhost:5432 instead. Any PostgreSQL 13+ works.

set -euo pipefail

PORT="${LOCAL_DB_PORT:-55432}"
DB_NAME="${LOCAL_DB_NAME:-future_designer}"
DB_USER="${LOCAL_DB_USER:-devuser}"
BASE_DIR="${LOCAL_DB_HOME:-$HOME/.local/share/future-designer}"
PGDATA="$BASE_DIR/pgdata"
SOCK_DIR="$BASE_DIR/sock"
LOG_FILE="$BASE_DIR/postgres.log"

# Prefer the versioned Debian/Ubuntu path, then whatever is on PATH.
find_binary() {
    local name="$1"
    for candidate in /usr/lib/postgresql/*/bin/"$name"; do
        [ -x "$candidate" ] && { echo "$candidate"; return; }
    done
    command -v "$name" || {
        echo "error: $name not found. Install the PostgreSQL server package," >&2
        echo "       e.g. 'sudo apt install postgresql' or 'brew install postgresql@16'." >&2
        exit 1
    }
}

INITDB="$(find_binary initdb)"
PG_CTL="$(find_binary pg_ctl)"
CREATEDB="$(find_binary createdb)"
PSQL="$(command -v psql || find_binary psql)"

init_cluster() {
    if [ -d "$PGDATA" ]; then
        return
    fi
    echo "Creating a new cluster at $PGDATA"
    mkdir -p "$PGDATA" "$SOCK_DIR"
    chmod 700 "$PGDATA"
    # Trust auth is safe here: the server only listens on 127.0.0.1 and the data
    # directory is only readable by this user. Never use it for anything exposed.
    "$INITDB" -D "$PGDATA" -U "$DB_USER" --auth=trust --encoding=UTF8 --locale=C >/dev/null
}

is_running() {
    "$PG_CTL" -D "$PGDATA" status >/dev/null 2>&1
}

case "${1:-start}" in
    start)
        init_cluster
        if is_running; then
            echo "Already running on port $PORT"
        else
            mkdir -p "$SOCK_DIR"
            "$PG_CTL" -D "$PGDATA" \
                -o "-p $PORT -k $SOCK_DIR -c listen_addresses=127.0.0.1" \
                -l "$LOG_FILE" start
        fi
        # Wait for it to accept connections before trying to create the database.
        for _ in $(seq 1 30); do
            "$PSQL" -h 127.0.0.1 -p "$PORT" -U "$DB_USER" -d postgres -c 'select 1' >/dev/null 2>&1 && break
            sleep 0.5
        done
        if ! "$PSQL" -h 127.0.0.1 -p "$PORT" -U "$DB_USER" -lqt 2>/dev/null | cut -d\| -f1 | grep -qw "$DB_NAME"; then
            "$CREATEDB" -h 127.0.0.1 -p "$PORT" -U "$DB_USER" "$DB_NAME"
            echo "Created database '$DB_NAME'"
        fi
        echo "PostgreSQL ready:  jdbc:postgresql://127.0.0.1:$PORT/$DB_NAME  (user $DB_USER, no password)"
        ;;

    stop)
        if is_running; then
            "$PG_CTL" -D "$PGDATA" stop
        else
            echo "Not running"
        fi
        ;;

    status)
        if is_running; then
            echo "Running on port $PORT — data in $PGDATA"
            "$PSQL" -h 127.0.0.1 -p "$PORT" -U "$DB_USER" -d "$DB_NAME" -c \
                "select count(*) as processes from process" 2>/dev/null || true
        else
            echo "Stopped. Data directory: $PGDATA"
        fi
        ;;

    psql)
        shift
        exec "$PSQL" -h 127.0.0.1 -p "$PORT" -U "$DB_USER" -d "$DB_NAME" "$@"
        ;;

    reset)
        # Drops the schema; the next backend start replays every migration from V1.
        "$PSQL" -h 127.0.0.1 -p "$PORT" -U "$DB_USER" -d "$DB_NAME" \
            -c 'DROP SCHEMA public CASCADE; CREATE SCHEMA public;'
        echo "Schema dropped. Restart the backend to re-run migrations and reseed."
        ;;

    *)
        echo "usage: $0 {start|stop|status|psql|reset}" >&2
        exit 1
        ;;
esac
