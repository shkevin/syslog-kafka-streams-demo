#!/bin/bash
: "
    Script Name: entrypoint.sh
    Description: This script is the entrypoint for the application. 
    Args       : None
    Author     : Kevin Cox
    Email      : kcox@confluent.io
  "

set -u # Unbouded variables
set -e # Exit if error

GREEN='\033[0;32m'
RED='\033[0;31m'
WHITE='\033[0;37m'
CYAN='\033[0;36m'
RESET='\033[0m'

# Catch Errors
error() {
    local parent_lineno="$1"
    local message="${2-}"
    local code="${3:-}"

    if [[ -n "${2-}" ]]; then
        printf "${RED}Error on or near line ${parent_lineno}: ${message}; exiting with status ${code}\n"
    else
        printf "${RED}Error on or near line ${parent_lineno}; exiting with status ${code}\n"
    fi
    exit "${code}"
}
trap 'error ${LINENO}' ERR

echo "Bootstrap servers: $BOOTSTRAP_SERVERS"
echo "Schema Registry URL: $SCHEMA_REGISTRY_URL"

# Wait for Kafka to be available
printf "${CYAN}Waiting for Kafka...${RESET}\n"
until $(nc -z broker 29092); do
    printf '.'
    sleep 1
done
printf "\n${GREEN}Kafka is up.${RESET}\n"

# Wait for Schema Registry to be available
printf "${CYAN}Waiting for Schema Registry...${RESET}\n"
until $(curl --output /dev/null --silent --head --fail ${SCHEMA_REGISTRY_URL}); do
    printf '.'
    sleep 1
done
printf "\n${GREEN}Schema Registry is up.${RESET}\n"

# Register schemas
# This doesn't currently use the cached deps from builder stage.
# fixing this would speed up the build significantly.
printf "${CYAN}Registering schemas...${RESET}\n"
./mvnw -B schema-registry:register -Dschema-registry-url=$SCHEMA_REGISTRY_URL

# Mark the application as ready
touch /tmp/app-ready

# Proceed to run the application
exec java -jar /app/app.jar

printf "${GREEN}Done${RESET}\n"

exec "$@"