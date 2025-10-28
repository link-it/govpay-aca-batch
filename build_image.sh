#!/bin/bash

##############################################################################
# GovPay A.C.A. Docker Image Build Script
#
# Build a Docker image for GovPay A.C.A. (Avvisi Cortesia Automatici)
# batch processor. JDBC drivers are mounted externally at runtime.
#
# Usage: ./build_image.sh [options]
#
# Options:
#   -v VERSION    GovPay installer version (default: 3.8.0)
#   -a VERSION    ACA release version (default: 1.1.3)
#   -t TAG        Additional tag for the image (optional)
#   -h            Show this help message
#
# Examples:
#   ./build_image.sh                          # Build with defaults
#   ./build_image.sh -v 3.8.0 -a 1.1.3       # Specify versions
#   ./build_image.sh -t latest               # Add latest tag
##############################################################################

set -e

# Default values
GOVPAY_VERSION="3.8.0"
GOVPAY_ACA_VERSION="1.1.3"
IMAGE_NAME="linkitaly/govpay-aca"
ADDITIONAL_TAG=""

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored messages
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to show usage
show_usage() {
    grep '^#' "$0" | grep -v '#!/bin/bash' | sed 's/^# \?//'
    exit 0
}

# Parse command line arguments
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
            print_error "Invalid option: -$OPTARG"
            show_usage
            ;;
    esac
done

# Check if required files exist
if [ ! -f "Dockerfile.github" ]; then
    print_error "Dockerfile.github not found in current directory"
    exit 1
fi

if [ ! -f "entrypoint.sh" ]; then
    print_error "entrypoint.sh not found in current directory"
    exit 1
fi

if [ ! -f "init_aca_db.sh" ]; then
    print_error "init_aca_db.sh not found in current directory"
    exit 1
fi

# Build image tags (no database suffix - drivers are external)
MAIN_TAG="${IMAGE_NAME}:${GOVPAY_ACA_VERSION}"
TAGS="-t ${MAIN_TAG}"

if [ -n "${ADDITIONAL_TAG}" ]; then
    TAGS="${TAGS} -t ${IMAGE_NAME}:${ADDITIONAL_TAG}"
fi

# Print build information
print_info "====================================="
print_info "GovPay A.C.A. Docker Build"
print_info "====================================="
print_info "GovPay Version:  ${GOVPAY_VERSION}"
print_info "ACA Version:     ${GOVPAY_ACA_VERSION}"
print_info "Image tags:      ${MAIN_TAG}"
if [ -n "${ADDITIONAL_TAG}" ]; then
    print_info "                 ${IMAGE_NAME}:${ADDITIONAL_TAG}"
fi
print_info "====================================="

# Build the image
print_info "Building Docker image..."
docker build \
    -f Dockerfile.github \
    --build-arg GOVPAY_VERSION="${GOVPAY_VERSION}" \
    --build-arg GOVPAY_ACA_VERSION="${GOVPAY_ACA_VERSION}" \
    ${TAGS} \
    .

# Check build result
if [ $? -eq 0 ]; then
    print_info "====================================="
    print_info "${GREEN}Build completed successfully!${NC}"
    print_info "====================================="
    print_info "Image: ${MAIN_TAG}"
    if [ -n "${ADDITIONAL_TAG}" ]; then
        print_info "       ${IMAGE_NAME}:${ADDITIONAL_TAG}"
    fi
    print_info ""
    print_info "IMPORTANT: JDBC drivers must be provided at runtime!"
    print_info "Place JDBC driver JAR files in ./jdbc-drivers/ directory"
    print_info ""
    print_info "To run the container:"
    print_info "  1. Copy .env.template to .env and configure"
    print_info "  2. Place JDBC drivers in ./jdbc-drivers/"
    print_info "  3. docker-compose up -d"
    print_info ""
    print_info "Or manually:"
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
    print_error "Build failed!"
    exit 1
fi
