#!/bin/bash

##############################################################################
# GovPay ACA Database Initialization Script
#
# This script initializes the ACA database by:
# 1. Performing liveness check (TCP connection to database)
# 2. Performing readiness check (checking if tables exist)
# 3. Executing SQL scripts if database is not initialized
#
# Inspired by govpay-docker/initgovpay.sh
##############################################################################

set -e

# Configuration with defaults
GOVPAY_ACA_POP_DB_SKIP=${GOVPAY_ACA_POP_DB_SKIP:-TRUE}
GOVPAY_ACA_DB_CHECK_TABLE=${GOVPAY_ACA_DB_CHECK_TABLE:-aca_posizioni_pendenti}
GOVPAY_ACA_LIVE_DB_CHECK_SKIP=${GOVPAY_ACA_LIVE_DB_CHECK_SKIP:-FALSE}
GOVPAY_ACA_READY_DB_CHECK_SKIP=${GOVPAY_ACA_READY_DB_CHECK_SKIP:-FALSE}
GOVPAY_ACA_LIVE_DB_CHECK_MAX_RETRY=${GOVPAY_ACA_LIVE_DB_CHECK_MAX_RETRY:-30}
GOVPAY_ACA_LIVE_DB_CHECK_SLEEP_TIME=${GOVPAY_ACA_LIVE_DB_CHECK_SLEEP_TIME:-2}
GOVPAY_ACA_LIVE_DB_CHECK_CONNECT_TIMEOUT=${GOVPAY_ACA_LIVE_DB_CHECK_CONNECT_TIMEOUT:-5}
GOVPAY_ACA_READY_DB_CHECK_MAX_RETRY=${GOVPAY_ACA_READY_DB_CHECK_MAX_RETRY:-5}
GOVPAY_ACA_READY_DB_CHECK_SLEEP_TIME=${GOVPAY_ACA_READY_DB_CHECK_SLEEP_TIME:-2}

# Logging functions
log_info() {
    echo "[INFO] $(date '+%Y-%m-%d %H:%M:%S') - $1"
}

log_warn() {
    echo "[WARN] $(date '+%Y-%m-%d %H:%M:%S') - $1"
}

log_error() {
    echo "[ERROR] $(date '+%Y-%m-%d %H:%M:%S') - $1" >&2
}

# Skip if population is disabled
if [ "${GOVPAY_ACA_POP_DB_SKIP^^}" == "TRUE" ]; then
    log_info "Database initialization skipped (GOVPAY_ACA_POP_DB_SKIP=TRUE)"
    exit 0
fi

log_info "========================================"
log_info "GovPay ACA Database Initialization"
log_info "========================================"

# Extract server and port from GOVPAY_DB_SERVER
IFS=':' read -r DB_HOST DB_PORT <<< "${GOVPAY_DB_SERVER}"

# Set default ports if not specified
if [ -z "${DB_PORT}" ] || [ "${DB_PORT}" == "${DB_HOST}" ]; then
    case "${GOVPAY_DB_TYPE}" in
        postgresql) DB_PORT=5432 ;;
        mysql|mariadb) DB_PORT=3306 ;;
        oracle) DB_PORT=1521 ;;
        *) DB_PORT=5432 ;;
    esac
fi

log_info "Database type: ${GOVPAY_DB_TYPE}"
log_info "Database server: ${DB_HOST}:${DB_PORT}"
log_info "Database name: ${GOVPAY_DB_NAME}"

##############################################################################
# LIVENESS CHECK (TCP Connection)
##############################################################################

if [ "${GOVPAY_ACA_LIVE_DB_CHECK_SKIP^^}" == "FALSE" ]; then
    log_info "Performing liveness check (TCP connection)..."
    RETRY=0
    DB_ALIVE=1

    while [ ${DB_ALIVE} -ne 0 ] && [ ${RETRY} -lt ${GOVPAY_ACA_LIVE_DB_CHECK_MAX_RETRY} ]; do
        nc -w ${GOVPAY_ACA_LIVE_DB_CHECK_CONNECT_TIMEOUT} -z "${DB_HOST}" "${DB_PORT}"
        DB_ALIVE=$?
        RETRY=$((RETRY + 1))

        if [ ${DB_ALIVE} -ne 0 ]; then
            log_info "Database not ready, retry ${RETRY}/${GOVPAY_ACA_LIVE_DB_CHECK_MAX_RETRY}..."
            sleep ${GOVPAY_ACA_LIVE_DB_CHECK_SLEEP_TIME}
        fi
    done

    if [ ${DB_ALIVE} -ne 0 ]; then
        log_error "FATAL: Database not reachable after ${GOVPAY_ACA_LIVE_DB_CHECK_MAX_RETRY} attempts"
        exit 1
    fi

    log_info "Liveness check passed"
fi

##############################################################################
# BUILD JDBC URL
##############################################################################

case "${GOVPAY_DB_TYPE}" in
    postgresql)
        JDBC_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${GOVPAY_DB_NAME}"
        START_TRANSACTION="START TRANSACTION;"
        ;;
    mysql|mariadb)
        JDBC_URL="jdbc:mysql://${DB_HOST}:${DB_PORT}/${GOVPAY_DB_NAME}"
        START_TRANSACTION="START TRANSACTION;"
        ;;
    oracle)
        if [ "${GOVPAY_ORACLE_JDBC_URL_TYPE:-servicename}" == "servicename" ]; then
            JDBC_URL="jdbc:oracle:thin:@//${DB_HOST}:${DB_PORT}/${GOVPAY_DB_NAME}"
        else
            JDBC_URL="jdbc:oracle:thin:@${DB_HOST}:${DB_PORT}:${GOVPAY_DB_NAME}"
        fi
        START_TRANSACTION=""
        ;;
    *)
        log_error "Unsupported database type: ${GOVPAY_DB_TYPE}"
        exit 1
        ;;
esac

# Add connection params if present
if [ -n "${GOVPAY_DS_CONN_PARAM}" ]; then
    if [[ "${JDBC_URL}" == *"?"* ]]; then
        JDBC_URL="${JDBC_URL}&${GOVPAY_DS_CONN_PARAM}"
    else
        JDBC_URL="${JDBC_URL}?${GOVPAY_DS_CONN_PARAM}"
    fi
fi

log_info "JDBC URL: ${JDBC_URL}"

##############################################################################
# CREATE SQLTOOL RC FILE
##############################################################################

SQLTOOL_RC="/tmp/sqltool_aca.rc"
cat > ${SQLTOOL_RC} <<EOSQLTOOL
urlid aca_db
url ${JDBC_URL}
username ${GOVPAY_DB_USER}
password ${GOVPAY_DB_PASSWORD}
driver ${GOVPAY_DS_DRIVER_CLASS}
transiso TRANSACTION_READ_COMMITTED
charset UTF-8
EOSQLTOOL

##############################################################################
# READINESS CHECK (Table Existence)
##############################################################################

if [ "${GOVPAY_ACA_READY_DB_CHECK_SKIP^^}" == "FALSE" ]; then
    log_info "Performing readiness check (table: ${GOVPAY_ACA_DB_CHECK_TABLE})..."

    # Build check query based on database type
    case "${GOVPAY_DB_TYPE}" in
        postgresql)
            CHECK_QUERY="SELECT count(*) FROM information_schema.tables WHERE LOWER(table_name)='${GOVPAY_ACA_DB_CHECK_TABLE,,}' AND LOWER(table_catalog)='${GOVPAY_DB_NAME,,}';"
            ;;
        mysql|mariadb)
            CHECK_QUERY="SELECT count(*) FROM information_schema.tables WHERE LOWER(table_name)='${GOVPAY_ACA_DB_CHECK_TABLE,,}' AND LOWER(table_schema)='${GOVPAY_DB_NAME,,}';"
            ;;
        oracle)
            CHECK_QUERY="SELECT count(*) FROM all_tables WHERE LOWER(table_name)='${GOVPAY_ACA_DB_CHECK_TABLE,,}' AND LOWER(owner)='${GOVPAY_DB_USER^^}';"
            ;;
    esac

    RETRY=0
    TABLE_EXISTS=-1

    while [ ${TABLE_EXISTS} -lt 0 ] && [ ${RETRY} -lt ${GOVPAY_ACA_READY_DB_CHECK_MAX_RETRY} ]; do
        TABLE_COUNT=$(java -Dfile.encoding=UTF-8 \
            -cp "${GOVPAY_DS_JDBC_LIBS}/*:/opt/sqltool.jar" \
            org.hsqldb.cmdline.SqlTool \
            --rcFile=${SQLTOOL_RC} \
            --sql="${CHECK_QUERY}" \
            aca_db 2>/dev/null | tail -1 | tr -d ' \n\r')

        if [[ "${TABLE_COUNT}" =~ ^[0-9]+$ ]]; then
            TABLE_EXISTS=${TABLE_COUNT}
        else
            RETRY=$((RETRY + 1))
            if [ ${RETRY} -lt ${GOVPAY_ACA_READY_DB_CHECK_MAX_RETRY} ]; then
                log_info "Readiness check failed, retry ${RETRY}/${GOVPAY_ACA_READY_DB_CHECK_MAX_RETRY}..."
                sleep ${GOVPAY_ACA_READY_DB_CHECK_SLEEP_TIME}
            fi
        fi
    done

    if [ ${TABLE_EXISTS} -lt 0 ]; then
        log_error "FATAL: Readiness check failed after ${GOVPAY_ACA_READY_DB_CHECK_MAX_RETRY} attempts"
        exit 1
    fi

    log_info "Readiness check result: ${TABLE_EXISTS} table(s) found"

    if [ ${TABLE_EXISTS} -gt 0 ]; then
        log_info "Database already initialized, skipping SQL execution"
        exit 0
    fi
fi

##############################################################################
# SQL SCRIPT EXECUTION
##############################################################################

log_info "Initializing database..."

# Determine SQL directory (mariadb uses mysql scripts)
SQL_DIR="${GOVPAY_DB_TYPE}"
[ "${GOVPAY_DB_TYPE}" == "mariadb" ] && SQL_DIR="mysql"

# Check if SQL scripts exist
SQL_FILE="/opt/sql/sql/${SQL_DIR}/tabelle_batch-create.sql"
if [ ! -f "${SQL_FILE}" ]; then
    log_error "SQL script not found: ${SQL_FILE}"
    log_error "Available files in /opt/sql:"
    ls -la /opt/sql/ 2>/dev/null || echo "  /opt/sql directory not found"
    if [ -d "/opt/sql/${SQL_DIR}" ]; then
        log_error "Files in /opt/sql/${SQL_DIR}:"
        ls -la "/opt/sql/${SQL_DIR}/" 2>/dev/null
    fi
    exit 1
fi

# Copy scripts to temporary location
mkdir -p /var/tmp/aca_sql
cp "${SQL_FILE}" /var/tmp/aca_sql/
log_info "SQL script copied to /var/tmp/aca_sql/"

# Apply vendor-specific transformations
case "${GOVPAY_DB_TYPE}" in
    mysql|mariadb)
        log_info "Applying MySQL/MariaDB transformations..."
        # Remove escaped quotes in COMMENT statements
        sed -i -e "/COMMENT/s%\\\\'% %g" /var/tmp/aca_sql/tabelle_batch-create.sql
        ;;
    oracle)
        log_info "Applying Oracle transformations..."
        # Enable raw mode for triggers and functions
        sed -i -r -e '/^CREATE( OR REPLACE)? (TRIGGER|FUNCTION|PROCEDURE)/i .' \
                  -e 's/^\/$/.\n:;/' /var/tmp/aca_sql/tabelle_batch-create.sql
        ;;
esac

# Execute SQL scripts
log_info "Executing SQL script: tabelle_batch-create.sql"

java -Dfile.encoding=UTF-8 \
    -cp "${GOVPAY_DS_JDBC_LIBS}/*:/opt/sqltool.jar" \
    org.hsqldb.cmdline.SqlTool \
    --rcFile=${SQLTOOL_RC} \
    --continueOnErr=false \
    aca_db <<EOSQL
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
${START_TRANSACTION}
\i /var/tmp/aca_sql/tabelle_batch-create.sql
COMMIT;
EOSQL

SQL_EXIT_CODE=$?

# Clean up
rm -rf /var/tmp/aca_sql
rm -f ${SQLTOOL_RC}

if [ ${SQL_EXIT_CODE} -eq 0 ]; then
    log_info "========================================"
    log_info "Database initialization completed successfully"
    log_info "========================================"
    exit 0
else
    log_error "========================================"
    log_error "Database initialization failed with exit code: ${SQL_EXIT_CODE}"
    log_error "========================================"
    exit ${SQL_EXIT_CODE}
fi
