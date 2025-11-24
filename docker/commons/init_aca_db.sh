#!/bin/bash -x

##############################################################################
# Script di Inizializzazione Database GovPay ACA
#
# Questo script inizializza il database ACA eseguendo:
# 1. Liveness check (connessione TCP al database)
# 2. Readiness check (verifica esistenza tabelle)
# 3. Esecuzione script SQL se il database non è inizializzato
#
# Ispirato a govpay-docker/initgovpay.sh
##############################################################################
set -x
set -e

# Configurazione con valori di default
GOVPAY_ACA_POP_DB_SKIP=${GOVPAY_ACA_POP_DB_SKIP:-TRUE}
GOVPAY_ACA_DB_CHECK_TABLE=${GOVPAY_ACA_DB_CHECK_TABLE:-batch_job_execution_context}
GOVPAY_ACA_LIVE_DB_CHECK_SKIP=${GOVPAY_ACA_LIVE_DB_CHECK_SKIP:-FALSE}
GOVPAY_ACA_READY_DB_CHECK_SKIP=${GOVPAY_ACA_READY_DB_CHECK_SKIP:-FALSE}
GOVPAY_ACA_LIVE_DB_CHECK_MAX_RETRY=${GOVPAY_ACA_LIVE_DB_CHECK_MAX_RETRY:-30}
GOVPAY_ACA_LIVE_DB_CHECK_SLEEP_TIME=${GOVPAY_ACA_LIVE_DB_CHECK_SLEEP_TIME:-2}
GOVPAY_ACA_LIVE_DB_CHECK_CONNECT_TIMEOUT=${GOVPAY_ACA_LIVE_DB_CHECK_CONNECT_TIMEOUT:-5}
GOVPAY_ACA_READY_DB_CHECK_MAX_RETRY=${GOVPAY_ACA_READY_DB_CHECK_MAX_RETRY:-5}
GOVPAY_ACA_READY_DB_CHECK_SLEEP_TIME=${GOVPAY_ACA_READY_DB_CHECK_SLEEP_TIME:-2}

# Funzioni di logging
log_info() {
    echo "[INFO] $(date '+%Y-%m-%d %H:%M:%S') - $1"
}

log_warn() {
    echo "[WARN] $(date '+%Y-%m-%d %H:%M:%S') - $1"
}

log_error() {
    echo "[ERROR] $(date '+%Y-%m-%d %H:%M:%S') - $1" >&2
}

# Salta se l'inizializzazione è disabilitata
if [ "${GOVPAY_ACA_POP_DB_SKIP^^}" == "TRUE" ]; then
    log_info "Inizializzazione database saltata (GOVPAY_ACA_POP_DB_SKIP=TRUE)"
    exit 0
fi

log_info "========================================"
log_info "Inizializzazione Database GovPay ACA"
log_info "========================================"

# Estrazione server e porta da GOVPAY_DB_SERVER
IFS=':' read -r DB_HOST DB_PORT <<< "${GOVPAY_DB_SERVER}"

# Imposta porte di default se non specificate
if [ -z "${DB_PORT}" ] || [ "${DB_PORT}" == "${DB_HOST}" ]; then
    case "${GOVPAY_DB_TYPE}" in
        postgresql) DB_PORT=5432 ;;
        mysql|mariadb) DB_PORT=3306 ;;
        oracle) DB_PORT=1521 ;;
        *) DB_PORT=5432 ;;
    esac
fi

log_info "Tipo database: ${GOVPAY_DB_TYPE}"
log_info "Server database: ${DB_HOST}:${DB_PORT}"
log_info "Nome database: ${GOVPAY_DB_NAME}"

##############################################################################
# LIVENESS CHECK (Connessione TCP)
##############################################################################

if [ "${GOVPAY_ACA_LIVE_DB_CHECK_SKIP^^}" == "FALSE" ]; then
    log_info "Esecuzione liveness check (connessione TCP)..."
    RETRY=0
    DB_ALIVE=1

    while [ ${DB_ALIVE} -ne 0 ] && [ ${RETRY} -lt ${GOVPAY_ACA_LIVE_DB_CHECK_MAX_RETRY} ]; do
        nc -w ${GOVPAY_ACA_LIVE_DB_CHECK_CONNECT_TIMEOUT} -z "${DB_HOST}" "${DB_PORT}"
        DB_ALIVE=$?
        RETRY=$((RETRY + 1))

        if [ ${DB_ALIVE} -ne 0 ]; then
            log_info "Database non pronto, tentativo ${RETRY}/${GOVPAY_ACA_LIVE_DB_CHECK_MAX_RETRY}..."
            sleep ${GOVPAY_ACA_LIVE_DB_CHECK_SLEEP_TIME}
        fi
    done

    if [ ${DB_ALIVE} -ne 0 ]; then
        log_error "FATALE: Database non raggiungibile dopo ${GOVPAY_ACA_LIVE_DB_CHECK_MAX_RETRY} tentativi"
        exit 1
    fi

    log_info "Liveness check superato"
fi

##############################################################################
# COSTRUZIONE URL JDBC
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
        log_error "Tipo database non supportato: ${GOVPAY_DB_TYPE}"
        exit 1
        ;;
esac

# Aggiunta parametri di connessione se presenti
if [ -n "${GOVPAY_DS_CONN_PARAM}" ]; then
    if [[ "${JDBC_URL}" == *"?"* ]]; then
        JDBC_URL="${JDBC_URL}&${GOVPAY_DS_CONN_PARAM}"
    else
        JDBC_URL="${JDBC_URL}?${GOVPAY_DS_CONN_PARAM}"
    fi
fi

log_info "URL JDBC: ${JDBC_URL}"

##############################################################################
# CREAZIONE FILE RC SQLTOOL
##############################################################################

SQLTOOL_RC_FILE="/tmp/sqltool_aca.rc"
cat > ${SQLTOOL_RC_FILE} <<EOSQLTOOL
urlid aca_db
url ${JDBC_URL}
username ${GOVPAY_DB_USER}
password ${GOVPAY_DB_PASSWORD}
driver ${GOVPAY_DS_DRIVER_CLASS}
transiso TRANSACTION_READ_COMMITTED
charset UTF-8
EOSQLTOOL

##############################################################################
# READINESS CHECK (Esistenza Tabella)
##############################################################################

if [ "${GOVPAY_ACA_READY_DB_CHECK_SKIP^^}" == "FALSE" ]; then
    log_info "Esecuzione readiness check (tabella: ${GOVPAY_ACA_DB_CHECK_TABLE})..."

    # Costruzione query di verifica in base al tipo di database
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

    INVOCAZIONE_CLIENT="-Dfile.encoding=UTF-8 -cp ${GOVPAY_DS_JDBC_LIBS}/*:/opt/hsqldb-${HSQLDB_FULLVERSION}/hsqldb/lib/sqltool.jar org.hsqldb.cmdline.SqlTool --rcFile=${SQLTOOL_RC_FILE} "

    while [ ${TABLE_EXISTS} -lt 0 ] && [ ${RETRY} -lt ${GOVPAY_ACA_READY_DB_CHECK_MAX_RETRY} ]; do
        TABLE_COUNT=$(java ${INVOCAZIONE_CLIENT} \
            --sql="${CHECK_QUERY}" \
            aca_db 2>/dev/null | tail -1 | tr -d ' \n\r')

        if [[ "${TABLE_COUNT}" =~ ^[0-9]+$ ]]; then
            TABLE_EXISTS=${TABLE_COUNT}
        else
            RETRY=$((RETRY + 1))
            if [ ${RETRY} -lt ${GOVPAY_ACA_READY_DB_CHECK_MAX_RETRY} ]; then
                log_info "Readiness check fallito, tentativo ${RETRY}/${GOVPAY_ACA_READY_DB_CHECK_MAX_RETRY}..."
                sleep ${GOVPAY_ACA_READY_DB_CHECK_SLEEP_TIME}
            fi
        fi
    done

    if [ ${TABLE_EXISTS} -lt 0 ]; then
        log_error "FATALE: Readiness check fallito dopo ${GOVPAY_ACA_READY_DB_CHECK_MAX_RETRY} tentativi"
        exit 1
    fi

    log_info "Risultato readiness check: ${TABLE_EXISTS} tabella/e trovata/e"

    if [ ${TABLE_EXISTS} -gt 0 ]; then
        log_info "Database già inizializzato, esecuzione SQL saltata"
        exit 0
    fi
fi

##############################################################################
# ESECUZIONE SCRIPT SQL
##############################################################################

log_info "Inizializzazione database in corso..."

# Determina directory SQL (mariadb usa gli script mysql)
SQL_DIR="${GOVPAY_DB_TYPE}"
[ "${GOVPAY_DB_TYPE}" == "mariadb" ] && SQL_DIR="mysql"

# Verifica esistenza script SQL
SQL_FILE="/opt/sql/${SQL_DIR}/tabelle_batch-create.sql"
if [ ! -f "${SQL_FILE}" ]; then
    log_error "Script SQL non trovato: ${SQL_FILE}"
    log_error "File disponibili in /opt/sql:"
    ls -la /opt/sql/ 2>/dev/null || echo "  directory /opt/sql non trovata"
    if [ -d "/opt/sql/${SQL_DIR}" ]; then
        log_error "File in /opt/sql/${SQL_DIR}:"
        ls -la "/opt/sql/${SQL_DIR}/" 2>/dev/null
    fi
    exit 1
fi

# Copia script in posizione temporanea
mkdir -p /tmp/aca_sql
cp "/opt/sql/${SQL_DIR}"/*.sql /tmp/aca_sql/
log_info "Script SQL copiati in /tmp/aca_sql/"

# Applica trasformazioni specifiche per vendor
case "${GOVPAY_DB_TYPE}" in
    mysql|mariadb)
        log_info "Applicazione trasformazioni MySQL/MariaDB..."
        # Rimuove quote escaped negli statement COMMENT
        sed -i -e "/COMMENT/s%\\\\'% %g" /tmp/aca_sql/*.sql
        ;;
    oracle)
        log_info "Applicazione trasformazioni Oracle..."
        # Abilita modalità raw per trigger e funzioni
        sed -i -r -e '/^CREATE( OR REPLACE)? (TRIGGER|FUNCTION|PROCEDURE)/i .' \
                  -e 's/^\/$/.\n:;/' /tmp/aca_sql/*.sql
        ;;
esac

# Esecuzione script SQL
log_info "Esecuzione script SQL in corso..."

java ${INVOCAZIONE_CLIENT} \
    --continueOnErr=false \
    aca_db <<EOSQL
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
${START_TRANSACTION}
\i /tmp/aca_sql/tabelle_batch-create.sql
\i /tmp/aca_sql/create-db.sql
COMMIT;
EOSQL

SQL_EXIT_CODE=$?

# Pulizia
#rm -rf /tmp/aca_sql
#rm -f ${SQLTOOL_RC_FILE}

if [ ${SQL_EXIT_CODE} -eq 0 ]; then
    log_info "========================================"
    log_info "Inizializzazione database completata con successo"
    log_info "========================================"
    exit 0
else
    log_error "========================================"
    log_error "Inizializzazione database fallita con codice d'uscita: ${SQL_EXIT_CODE}"
    log_error "========================================"
    exit ${SQL_EXIT_CODE}
fi
