#!/bin/bash

##############################################################################
# GovPay A.C.A. (Avvisi Cortesia Automatici) - Script di Entrypoint
#
# Supporta nomenclatura variabili legacy e govpay-docker
##############################################################################

set -e

# Debug di esecuzione (come govpay-docker)
exec 6<> /tmp/entrypoint_debug.log
exec 2>&6
set -x

# Funzioni di logging
log_info() { echo -e "\033[0;32m[INFO]\033[0m $(date '+%Y-%m-%d %H:%M:%S') - $1"; }
log_warn() { echo -e "\033[1;33m[WARN]\033[0m $(date '+%Y-%m-%d %H:%M:%S') - $1"; }
log_error() { echo -e "\033[0;31m[ERROR]\033[0m $(date '+%Y-%m-%d %H:%M:%S') - $1"; }

log_info "========================================"
log_info "Avvio GovPay A.C.A. Batch Processor"
log_info "========================================"

##############################################################################
# Supporto variabili stile govpay-docker
##############################################################################



#
# Sanity check variabili minime attese
#
if [ -n "${GOVPAY_DB_TYPE}" -a -n "${GOVPAY_DB_SERVER}" -a -n  "${GOVPAY_DB_USER}" -a -n "${GOVPAY_DB_NAME}" ] 
then
        [ -n "${GOVPAY_DB_PASSWORD}" ] || log_warn "La variabile GOVPAY_DB_PASSWORD non è stata impostata."
        log_info "Sanity check variabili obbligatorie ... ok."
else
    log_error "Sanity check variabili obbligatorie ... fallito."
    log_error "Devono essere settate almeno le seguenti variabili obbligatorie:
GOVPAY_DB_TYPE: ${GOVPAY_DB_TYPE}
GOVPAY_DB_SERVER: ${GOVPAY_DB_SERVER}
GOVPAY_DB_NAME: ${GOVPAY_DB_NAME}
GOVPAY_DB_USER: ${GOVPAY_DB_USER}
"
    exit 1
fi


# Imposta valori di default
GOVPAY_DS_JDBC_LIBS=${GOVPAY_DS_JDBC_LIBS:-/opt/jdbc-drivers}
GOVPAY_ACA_MIN_POOL=${GOVPAY_ACA_MIN_POOL:-2}
GOVPAY_ACA_MAX_POOL=${GOVPAY_ACA_MAX_POOL:-10}




    # Estrazione host e porta
    IFS=':' read -r DB_HOST DB_PORT <<< "${GOVPAY_DB_SERVER}"
    [ -z "${DB_PORT}" ] && case "${GOVPAY_DB_TYPE}" in
        postgresql) DB_PORT=5432 ;;
        mysql|mariadb) DB_PORT=3306 ;;
        oracle) DB_PORT=1521 ;;
    esac

    # Costruzione URL JDBC
    case "${GOVPAY_DB_TYPE}" in
        postgresql)
            SPRING_DATASOURCE_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${GOVPAY_DB_NAME}"
            GOVPAY_DS_DRIVER_CLASS="org.postgresql.Driver"
            GOVPAY_HYBERNATE_DIALECT="org.hibernate.dialect.PostgreSQLDialect"
            ;;
        mysql|mariadb)
            SPRING_DATASOURCE_URL="jdbc:mysql://${DB_HOST}:${DB_PORT}/${GOVPAY_DB_NAME}"
            GOVPAY_DS_DRIVER_CLASS="com.mysql.cj.jdbc.Driver"
            GOVPAY_HYBERNATE_DIALECT="org.hibernate.dialect.MySQLDialect"
            ;;
        oracle)
            if [ "${GOVPAY_ORACLE_JDBC_URL_TYPE:-servicename}" == "servicename" ]; then
                SPRING_DATASOURCE_URL="jdbc:oracle:thin:@//${DB_HOST}:${DB_PORT}/${GOVPAY_DB_NAME}"
            else
                SPRING_DATASOURCE_URL="jdbc:oracle:thin:@${DB_HOST}:${DB_PORT}:${GOVPAY_DB_NAME}"
            fi
            GOVPAY_DS_DRIVER_CLASS="oracle.jdbc.OracleDriver"
            GOVPAY_HYBERNATE_DIALECT="org.hibernate.dialect.OracleDialect"
            [ -n "${ORACLE_TNS_ADMIN}" ] && JAVA_OPTS="${JAVA_OPTS} -Doracle.net.tns_admin=${ORACLE_TNS_ADMIN}"
            ;;
        *)
            log_error "GOVPAY_DB_TYPE non supportato: ${GOVPAY_DB_TYPE}"
            exit 1
            ;;
    esac

    # Aggiunta parametri di connessione
    [ -n "${GOVPAY_DS_CONN_PARAM}" ] && SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL}?${GOVPAY_DS_CONN_PARAM}"

    # Mappatura a variabili Spring

    export SPRING_DATASOURCE_URL
    export SPRING_DATASOURCE_USERNAME="${GOVPAY_DB_USER}"
    export SPRING_DATASOURCE_PASSWORD="${GOVPAY_DB_PASSWORD}"
    export SPRING_DATASOURCE_DRIVER_CLASS_NAME="${GOVPAY_DS_DRIVER_CLASS}"
    export SPRING_JPA_DATABASE_PLATFORM="${GOVPAY_HYBERNATE_DIALECT}"
    export SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT="${GOVPAY_HYBERNATE_DIALECT}"
    export SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="${GOVPAY_ACA_MIN_POOL}"
    export SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="${GOVPAY_ACA_MAX_POOL}"

    # Export per init_aca_db.sh
    export GOVPAY_DB_TYPE GOVPAY_DB_SERVER GOVPAY_DB_NAME GOVPAY_DB_USER GOVPAY_DB_PASSWORD
    export GOVPAY_DS_JDBC_LIBS GOVPAY_DS_DRIVER_CLASS GOVPAY_DS_CONN_PARAM

    log_info "Database: ${GOVPAY_DB_TYPE} su ${GOVPAY_DB_SERVER}/${GOVPAY_DB_NAME}"

##############################################################################
# Inizializzazione Database
##############################################################################

if [ "${GOVPAY_ACA_POP_DB_SKIP:-TRUE}" != "TRUE" ] && [ -n "${GOVPAY_DB_TYPE}" ]; then
    log_info "Esecuzione script di inizializzazione database..."
    /usr/local/bin/init_aca_db.sh
    if [ $? -ne 0 ]; then
        log_error "Inizializzazione database fallita"
        exit 1
    fi
fi

##############################################################################
# Configurazione URL Base GPD (Gestione Posizioni Debitorie)
##############################################################################

# Priorità 1: URL custom (se specificato, ha precedenza)
if [ -n "${GOVPAY_ACA_GPD_CUSTOMURL}" ]; then
    IT_GOVPAY_GPD_BATCH_CLIENT_BASEURL="${GOVPAY_ACA_GPD_CUSTOMURL}"
    log_info "Utilizzo URL GPD personalizzato: ${IT_GOVPAY_GPD_BATCH_CLIENT_BASEURL}"
# Priorità 2: Selezione URL basata su ambiente
elif [ -n "${GOVPAY_ACA_GPD_ENV}" ]; then
    # Conversione a minuscolo per confronto case-insensitive
    GPD_ENV=$(echo "${GOVPAY_ACA_GPD_ENV}" | tr '[:upper:]' '[:lower:]')

    case "${GPD_ENV}" in
        prod|produzione)
            IT_GOVPAY_GPD_BATCH_CLIENT_BASEURL="https://api.platform.pagopa.it/gpd/api/v1"
            log_info "Utilizzo ambiente GPD PRODUZIONE"
            ;;
        collaudo|test|stage|uat)
            IT_GOVPAY_GPD_BATCH_CLIENT_BASEURL="https://api.uat.platform.pagopa.it/gpd/api/v1"
            log_info "Utilizzo ambiente GPD UAT/TEST"
            ;;
        *)
            log_error "Valore GOVPAY_ACA_GPD_ENV non valido: ${GOVPAY_ACA_GPD_ENV}"
            log_error "Valori ammessi: collaudo, test, stage, uat, prod, produzione"
            exit 1
            ;;
    esac
    log_info "URL Base GPD: ${IT_GOVPAY_GPD_BATCH_CLIENT_BASEURL}"
fi

# Validazione configurazione GPD obbligatoria
if [ -z "${IT_GOVPAY_GPD_BATCH_CLIENT_BASEURL}" ]; then
    log_error "Configurazione URL Base GPD mancante"
    log_error "Impostare una di: GOVPAY_ACA_GPD_CUSTOMURL o GOVPAY_ACA_GPD_ENV"
    exit 1
fi
export IT_GOVPAY_GPD_BATCH_CLIENT_BASEURL

if [ -z "${GOVPAY_ACA_GPD_SUBSCRIPTIONKEY}" ]; then
    log_error "Subscription Key GPD mancante"
    log_error "Richiesta: GOVPAY_ACA_GPD_SUBSCRIPTIONKEY"
    exit 1
fi
export IT_GOVPAY_GPD_BATCH_CLIENT_HEADER_SUBSCRIPTIONKEY_VALUE="${GOVPAY_ACA_GPD_SUBSCRIPTIONKEY}"

##############################################################################
# Configurazione Cluster ID
##############################################################################

# Calcolo Cluster ID dall'indirizzo IP del container (da /etc/hosts)
IT_GOVPAY_GPD_BATCH_CLUSTERID=$(grep -E "[[:space:]]${HOSTNAME}[[:space:]]*" /etc/hosts | head -n 1 | awk '{print $1}')
# Fallback a hostname se estrazione IP fallisce
[ -z "${IT_GOVPAY_GPD_BATCH_CLUSTERID}" ] && IT_GOVPAY_GPD_BATCH_CLUSTERID=$(hostname)
export IT_GOVPAY_GPD_BATCH_CLUSTERID

##############################################################################
# Configurazione Integrazione GDE
##############################################################################

# Integrazione GDE disabilitata di default a meno che non venga fornito GOVPAY_ACA_GDE_URL
if [ -n "${GOVPAY_ACA_GDE_URL}" ]; then
    IT_GOVPAY_GDE_ENABLED=true
    IT_GOVPAY_GDE_CLIENT_BASEURL="${GOVPAY_ACA_GDE_URL}"
    export IT_GOVPAY_GDE_ENABLED IT_GOVPAY_GDE_CLIENT_BASEURL
    log_info "Integrazione GDE abilitata"
    log_info "URL GDE: ${IT_GOVPAY_GDE_CLIENT_BASEURL}"
else
    IT_GOVPAY_GDE_ENABLED=false
    export IT_GOVPAY_GDE_ENABLED
    log_info "Integrazione GDE disabilitata"
fi

##############################################################################
# Configurazione Modalità di Deploy
##############################################################################

# Verifica se modalità CRON è abilitata tramite GOVPAY_ACA_BATCH_USA_CRON
# Modalità CRON = batch si auto-schedula con intervallo (daemon con scheduler)
# Modalità ONE-SHOT = batch esegue una volta ed esce (senza scheduler)
# Valori ammessi: si, yes, 1, true (case insensitive)
 
case "${GOVPAY_ACA_BATCH_USA_CRON,,}" in
    si|yes|1|true)
        log_info "Modalità deployment: CRON (auto-schedulato)"
        SERVER_PORT=${SERVER_PORT:-10001}

        # Conversione intervallo da minuti a millisecondi (default: 5 minuti = 300000 ms)
        INTERVALLO_MINUTI=${GOVPAY_ACA_BATCH_INTERVALLO_CRON:-5}
        SCHEDULER_GPDSENDERJOB_FIXEDDELAYSTRING=$((INTERVALLO_MINUTI * 60 * 1000))

        export SERVER_PORT SCHEDULER_GPDSENDERJOB_FIXEDDELAYSTRING
        log_info "Porta Actuator: ${SERVER_PORT}, Intervallo scheduler: ${INTERVALLO_MINUTI} minuti (${SCHEDULER_GPDSENDERJOB_FIXEDDELAYSTRING}ms)"
        ;;
    *) 
        log_info "Modalità deployment: GESTITO (schedulato esternamente)"
        SPRING_MAIN_WEB_APPLICATION_TYPE="none"
        export SPRING_MAIN_WEB_APPLICATION_TYPE
        JAVA_OPTS="-Dspring.profiles.active=cron $JAVA_OPTS"
        ;;
esac




##############################################################################
# Configurazione Memoria JVM (Percentuale RAM)
##############################################################################

JAVA_OPTS="${JAVA_OPTS:-}"
DEFAULT_MAX_RAM_PERCENTAGE=80

JVM_MEMORY_OPTS="-XX:MaxRAMPercentage=${GOVPAY_ACA_JVM_MAX_RAM_PERCENTAGE:-${DEFAULT_MAX_RAM_PERCENTAGE}}"
[ -n "${GOVPAY_ACA_JVM_INITIAL_RAM_PERCENTAGE}" ] && JVM_MEMORY_OPTS="$JVM_MEMORY_OPTS -XX:InitialRAMPercentage=${GOVPAY_ACA_JVM_INITIAL_RAM_PERCENTAGE}"
[ -n "${GOVPAY_ACA_JVM_MIN_RAM_PERCENTAGE}" ] && JVM_MEMORY_OPTS="$JVM_MEMORY_OPTS -XX:MinRAMPercentage=${GOVPAY_ACA_JVM_MIN_RAM_PERCENTAGE}"
[ -n "${GOVPAY_ACA_JVM_MAX_METASPACE_SIZE}" ] && JVM_MEMORY_OPTS="$JVM_MEMORY_OPTS -XX:MaxMetaspaceSize=${GOVPAY_ACA_JVM_MAX_METASPACE_SIZE}"
[ -n "${GOVPAY_ACA_JVM_MAX_DIRECT_MEMORY_SIZE}" ] && JVM_MEMORY_OPTS="$JVM_MEMORY_OPTS -XX:MaxDirectMemorySize=${GOVPAY_ACA_JVM_MAX_DIRECT_MEMORY_SIZE}"

JAVA_OPTS="${JAVA_OPTS} ${JVM_MEMORY_OPTS}"
export JAVA_OPTS

##############################################################################
# Riepilogo Configurazione
##############################################################################

log_info "========================================"
log_info "Riepilogo Configurazione"
log_info "========================================"
log_info "Database: ${SPRING_DATASOURCE_URL}"
log_info "Pool: ${SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE}/${SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE}"
log_info "GPD: ${IT_GOVPAY_GPD_BATCH_CLIENT_BASEURL}"
log_info "GDE: ${IT_GOVPAY_GDE_ENABLED}"
log_info "Cluster ID: ${IT_GOVPAY_GPD_BATCH_CLUSTERID}"
log_info "Java: MaxRAMPercentage=${GOVPAY_ACA_JVM_MAX_RAM_PERCENTAGE:-${DEFAULT_MAX_RAM_PERCENTAGE}}%"
log_info "========================================"

##############################################################################
# Avvio Applicazione
##############################################################################

JAR_FILE=$(find /opt/govpay-aca -name "*.jar" -type f | head -n 1)

if [ -z "${JAR_FILE}" ]; then
    log_error "Nessun file JAR trovato in /opt/govpay-aca"
    exit 1
fi

log_info "Avvio: ${JAR_FILE}"
log_info "========================================"

exec java ${JAVA_OPTS} -jar "${JAR_FILE}"
