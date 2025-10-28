#!/bin/bash

##############################################################################
# GovPay A.C.A. (Avvisi Cortesia Automatici) Entrypoint Script
#
# Supports both legacy and govpay-docker compatible variable naming
##############################################################################

set -e

# Logging functions
log_info() { echo -e "\033[0;32m[INFO]\033[0m $(date '+%Y-%m-%d %H:%M:%S') - $1"; }
log_warn() { echo -e "\033[1;33m[WARN]\033[0m $(date '+%Y-%m-%d %H:%M:%S') - $1"; }
log_error() { echo -e "\033[0;31m[ERROR]\033[0m $(date '+%Y-%m-%d %H:%M:%S') - $1"; }

log_info "========================================"
log_info "GovPay A.C.A. Batch Processor Starting"
log_info "========================================"

##############################################################################
# Support for govpay-docker style variables
##############################################################################

# Set defaults
GOVPAY_DS_JDBC_LIBS=${GOVPAY_DS_JDBC_LIBS:-/opt/jdbc-drivers}
GOVPAY_ACA_MIN_POOL=${GOVPAY_ACA_MIN_POOL:-2}
GOVPAY_ACA_MAX_POOL=${GOVPAY_ACA_MAX_POOL:-10}

# If GOVPAY_DB_TYPE is set, use new variable style
if [ -n "${GOVPAY_DB_TYPE}" ]; then
    log_info "Using govpay-docker compatible variables"

    # Validate required govpay-docker variables
    if [ -z "${GOVPAY_DB_SERVER}" ] || [ -z "${GOVPAY_DB_NAME}" ] || \
       [ -z "${GOVPAY_DB_USER}" ] || [ -z "${GOVPAY_DB_PASSWORD}" ]; then
        log_error "Missing required GOVPAY_DB_* variables"
        log_error "Required: GOVPAY_DB_TYPE, GOVPAY_DB_SERVER, GOVPAY_DB_NAME, GOVPAY_DB_USER, GOVPAY_DB_PASSWORD"
        exit 1
    fi

    # Extract host and port
    IFS=':' read -r DB_HOST DB_PORT <<< "${GOVPAY_DB_SERVER}"
    [ -z "${DB_PORT}" ] && case "${GOVPAY_DB_TYPE}" in
        postgresql) DB_PORT=5432 ;;
        mysql|mariadb) DB_PORT=3306 ;;
        oracle) DB_PORT=1521 ;;
    esac

    # Build JDBC URL
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
            log_error "Unsupported GOVPAY_DB_TYPE: ${GOVPAY_DB_TYPE}"
            exit 1
            ;;
    esac

    # Add connection params
    [ -n "${GOVPAY_DS_CONN_PARAM}" ] && SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL}?${GOVPAY_DS_CONN_PARAM}"

    # Map to Spring variables
    SPRING_DATASOURCE_USERNAME="${GOVPAY_DB_USER}"
    SPRING_DATASOURCE_PASSWORD="${GOVPAY_DB_PASSWORD}"
    SPRING_DATASOURCE_DRIVER_CLASS_NAME="${GOVPAY_DS_DRIVER_CLASS}"
    SPRING_JPA_DATABASE_PLATFORM="${GOVPAY_HYBERNATE_DIALECT}"
    SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT="${GOVPAY_HYBERNATE_DIALECT}"
    SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="${GOVPAY_ACA_MIN_POOL}"
    SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="${GOVPAY_ACA_MAX_POOL}"

    export SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD
    export SPRING_DATASOURCE_DRIVER_CLASS_NAME SPRING_JPA_DATABASE_PLATFORM
    export SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT
    export SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE

    # Export for init_aca_db.sh
    export GOVPAY_DB_TYPE GOVPAY_DB_SERVER GOVPAY_DB_NAME GOVPAY_DB_USER GOVPAY_DB_PASSWORD
    export GOVPAY_DS_JDBC_LIBS GOVPAY_DS_DRIVER_CLASS GOVPAY_DS_CONN_PARAM

    log_info "Database: ${GOVPAY_DB_TYPE} at ${GOVPAY_DB_SERVER}/${GOVPAY_DB_NAME}"
else
    # Legacy variable style
    log_warn "Using legacy SPRING_DATASOURCE_* variables"
    log_warn "Consider migrating to GOVPAY_DB_* variables for consistency with govpay-docker"

    if [ -z "${SPRING_DATASOURCE_URL}" ] || [ -z "${SPRING_DATASOURCE_USERNAME}" ] || \
       [ -z "${SPRING_DATASOURCE_PASSWORD}" ]; then
        log_error "Missing required SPRING_DATASOURCE_* variables"
        exit 1
    fi
fi

##############################################################################
# Database Initialization
##############################################################################

if [ "${GOVPAY_ACA_POP_DB_SKIP:-TRUE}" != "TRUE" ] && [ -n "${GOVPAY_DB_TYPE}" ]; then
    log_info "Calling database initialization script..."
    /usr/local/bin/init_aca_db.sh
    if [ $? -ne 0 ]; then
        log_error "Database initialization failed"
        exit 1
    fi
fi

##############################################################################
# Validate GPD Configuration
##############################################################################

if [ -z "${IT_GOVPAY_GPD_BATCH_CLIENT_BASEURL}" ] || \
   [ -z "${IT_GOVPAY_GPD_BATCH_CLIENT_HEADER_SUBSCRIPTIONKEY_VALUE}" ]; then
    log_error "Missing required GPD configuration"
    log_error "Required: IT_GOVPAY_GPD_BATCH_CLIENT_BASEURL, IT_GOVPAY_GPD_BATCH_CLIENT_HEADER_SUBSCRIPTIONKEY_VALUE"
    exit 1
fi

##############################################################################
# Configure Cluster ID
##############################################################################

if [ -z "${IT_GOVPAY_GPD_BATCH_CLUSTERID}" ]; then
    IT_GOVPAY_GPD_BATCH_CLUSTERID=$(hostname)
    export IT_GOVPAY_GPD_BATCH_CLUSTERID
fi

##############################################################################
# Configure GDE Integration
##############################################################################

IT_GOVPAY_GDE_ENABLED=${IT_GOVPAY_GDE_ENABLED:-false}
export IT_GOVPAY_GDE_ENABLED

if [ "${IT_GOVPAY_GDE_ENABLED}" = "true" ]; then
    log_info "GDE integration enabled"
    [ -n "${IT_GOVPAY_GDE_CLIENT_BASEURL}" ] && log_info "GDE URL: ${IT_GOVPAY_GDE_CLIENT_BASEURL}"
else
    log_info "GDE integration disabled"
fi

##############################################################################
# Configure Deployment Mode
##############################################################################

DEPLOYMENT_MODE="${1:-systemd}"

case "${DEPLOYMENT_MODE}" in
    systemd)
        log_info "Deployment mode: SYSTEMD (daemon)"
        SERVER_PORT=${SERVER_PORT:-8080}
        SCHEDULER_GPDSENDERJOB_FIXEDDELAYSTRING=${SCHEDULER_GPDSENDERJOB_FIXEDDELAYSTRING:-300000}
        export SERVER_PORT SCHEDULER_GPDSENDERJOB_FIXEDDELAYSTRING
        log_info "Actuator port: ${SERVER_PORT}, Scheduler: ${SCHEDULER_GPDSENDERJOB_FIXEDDELAYSTRING}ms"
        ;;
    cron)
        log_info "Deployment mode: CRON (one-shot)"
        SPRING_MAIN_WEB_APPLICATION_TYPE="none"
        export SPRING_MAIN_WEB_APPLICATION_TYPE
        ;;
    *)
        log_error "Unknown deployment mode: ${DEPLOYMENT_MODE}"
        exit 1
        ;;
esac

##############################################################################
# Java Options
##############################################################################

JAVA_OPTS="${JAVA_OPTS:-}"
JAVA_MIN_HEAP=${JAVA_MIN_HEAP:-256m}
JAVA_MAX_HEAP=${JAVA_MAX_HEAP:-1024m}
JAVA_OPTS="${JAVA_OPTS} -Xms${JAVA_MIN_HEAP} -Xmx${JAVA_MAX_HEAP}"

##############################################################################
# Configuration Summary
##############################################################################

log_info "========================================"
log_info "Configuration Summary"
log_info "========================================"
log_info "Database: ${SPRING_DATASOURCE_URL}"
log_info "Pool: ${SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE}/${SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE}"
log_info "GPD: ${IT_GOVPAY_GPD_BATCH_CLIENT_BASEURL}"
log_info "GDE: ${IT_GOVPAY_GDE_ENABLED}"
log_info "Cluster ID: ${IT_GOVPAY_GPD_BATCH_CLUSTERID}"
log_info "Java: ${JAVA_MIN_HEAP} - ${JAVA_MAX_HEAP}"
log_info "========================================"

##############################################################################
# Start Application
##############################################################################

JAR_FILE=$(find /opt/govpay-aca -name "*.jar" -type f | head -n 1)

if [ -z "${JAR_FILE}" ]; then
    log_error "No JAR file found in /opt/govpay-aca"
    exit 1
fi

log_info "Starting: ${JAR_FILE}"
log_info "========================================"

exec java ${JAVA_OPTS} -jar "${JAR_FILE}"
