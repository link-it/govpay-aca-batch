#!/bin/bash

##############################################################################
# Script di Build Immagine Docker GovPay A.C.A.
#
# Costruisce un'immagine Docker per il batch processor GovPay A.C.A.
# (Avvisi Cortesia Automatici). I driver JDBC vengono montati esternamente
# a runtime.
#
# Uso: ./build_image.sh [opzioni]
#
# Opzioni:
#   -v VERSION    Versione installer GovPay (default: 3.8.0)
#   -a VERSION    Versione release ACA (default: 1.1.3)
#   -t TAG        Tag aggiuntivo per l'immagine (opzionale)
#   -h            Mostra questo messaggio di aiuto
#
# Esempi:
#   ./build_image.sh                          # Build con valori di default
#   ./build_image.sh -v 3.8.0 -a 1.1.3       # Specifica versioni
#   ./build_image.sh -t latest               # Aggiungi tag latest
##############################################################################

set -e

# Valori di default
GOVPAY_ACA_VERSION="1.1.3"
IMAGE_NAME="linkitaly/govpay-aca"
ADDITIONAL_TAG=""

# Output colorato
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # Nessun colore

# Funzione per stampare messaggi colorati
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Funzione per mostrare l'uso
show_usage() {
    grep '^#' "$0" | grep -v '#!/bin/bash' | sed 's/^# \?//'
    exit 0
}

# Parsing argomenti da linea di comando
while getopts "v:a:t:h" opt; do
    case ${opt} in
        v)
            GOVPAY_VERSION=$OPTARG
            ;;
        a)
            GOVPAY_ACA_VERSION=$OPTARG
            ;;
        t)
            ADDITIONAL_TAG=$OPTARG
            ;;
        h)
            show_usage
            ;;
        \?)
            print_error "Opzione non valida: -$OPTARG"
            show_usage
            ;;
    esac
done

# Verifica esistenza file richiesti
if [ ! -f "Dockerfile.github" ]; then
    print_error "Dockerfile.github non trovato nella directory corrente"
    exit 1
fi

if [ ! -f "entrypoint.sh" ]; then
    print_error "entrypoint.sh non trovato nella directory corrente"
    exit 1
fi

if [ ! -f "init_aca_db.sh" ]; then
    print_error "init_aca_db.sh non trovato nella directory corrente"
    exit 1
fi

# Costruzione tag immagine (senza suffisso database - driver esterni)
MAIN_TAG="${IMAGE_NAME}:${GOVPAY_ACA_VERSION}"
TAGS="-t ${MAIN_TAG}"

if [ -n "${ADDITIONAL_TAG}" ]; then
    TAGS="${TAGS} -t ${IMAGE_NAME}:${ADDITIONAL_TAG}"
fi

# Stampa informazioni di build
print_info "====================================="
print_info "Build Docker GovPay A.C.A."
print_info "====================================="
print_info "Versione ACA:     ${GOVPAY_ACA_VERSION}"
print_info "Tag immagine:     ${MAIN_TAG}"
if [ -n "${ADDITIONAL_TAG}" ]; then
    print_info "                  ${IMAGE_NAME}:${ADDITIONAL_TAG}"
fi
print_info "====================================="

# Build dell'immagine
print_info "Costruzione immagine Docker..."
docker build \
    -f Dockerfile.github \
    --build-arg GOVPAY_VERSION="${GOVPAY_VERSION}" \
    --build-arg GOVPAY_ACA_VERSION="${GOVPAY_ACA_VERSION}" \
    ${TAGS} \
    .

# Verifica risultato build
if [ $? -eq 0 ]; then
    print_info "====================================="
    print_info "${GREEN}Build completata con successo!${NC}"
    print_info "====================================="
    print_info "Immagine: ${MAIN_TAG}"
    if [ -n "${ADDITIONAL_TAG}" ]; then
        print_info "          ${IMAGE_NAME}:${ADDITIONAL_TAG}"
    fi
    print_info ""
    print_info "IMPORTANTE: I driver JDBC devono essere forniti a runtime!"
    print_info "Posizionare i file JAR dei driver JDBC nella directory ./jdbc-drivers/"
    print_info ""
    print_info "Per eseguire il container:"
    print_info "  1. Copiare .env.template in .env e configurare"
    print_info "  2. Posizionare i driver JDBC in ./jdbc-drivers/"
    print_info "  3. docker-compose up -d"
    print_info ""
    print_info "Oppure manualmente:"
    print_info "  docker run -d \\"
    print_info "    -e GOVPAY_DB_TYPE=postgresql \\"
    print_info "    -e GOVPAY_DB_SERVER=postgres:5432 \\"
    print_info "    -e GOVPAY_DB_NAME=govpay \\"
    print_info "    -e GOVPAY_DB_USER=govpay \\"
    print_info "    -e GOVPAY_DB_PASSWORD=<password> \\"
    print_info "    -e IT_GOVPAY_GPD_BATCH_CLIENT_BASEURL=<gpd-url> \\"
    print_info "    -e IT_GOVPAY_GPD_BATCH_CLIENT_HEADER_SUBSCRIPTIONKEY_VALUE=<key> \\"
    print_info "    -v \$(pwd)/jdbc-drivers:/opt/jdbc-drivers:ro \\"
    print_info "    -v govpay-aca-logs:/var/log/govpay \\"
    print_info "    ${MAIN_TAG}"
    print_info "====================================="
else
    print_error "Build fallita!"
    exit 1
fi
