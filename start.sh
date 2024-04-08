#!/bin/bash
: "
    Script Name: start.sh
    Description: This script starts the services and waits for them to be ready.
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

retry() {
    local -r -i max_wait="$1"; shift
    local -r cmd="$@"

    local -i sleep_interval=5
    local -i curr_wait=0

    until $cmd
    do
        if (( curr_wait >= max_wait ))
        then
            printf "${RED}ERROR: Failed after $curr_wait seconds. Please troubleshoot and run again.${RESET}\n"
            return 1
        else
            printf "."
            curr_wait=$((curr_wait+sleep_interval))
            sleep $sleep_interval
        fi
    done
    printf "\n"
}

check_connect_up() {
  containerName=$1

  FOUND=$(docker compose logs $containerName | grep "Herder started")
  if [ -z "$FOUND" ]; then
    return 1
  fi
  return 0
}

check_control_center_up() {
  containerName=$1

  FOUND=$(docker compose logs $containerName | grep "Started NetworkTrafficServerConnector")
  if [ -z "$FOUND" ]; then
    return 1
  fi
  return 0
}

function check_ksqlDB_host_running()
{
  if [[ $(curl -s http://localhost:8088/info | jq -r ".KsqlServerInfo.serverStatus") != *RUNNING* ]]; then
    return 1
  fi
  return 0
}

printf "${WHITE}Starting Services${RESET}\n"
docker-compose up -d

MAX_WAIT=180
printf "${CYAN}Waiting up to $MAX_WAIT seconds for control center to be ready${RESET}\n"
retry $MAX_WAIT check_control_center_up control-center || exit 1

MAX_WAIT=30
printf "${CYAN}Waiting up to $MAX_WAIT seconds for ksqlDB server to be ready to serve requests${RESET}\n"
retry $MAX_WAIT check_ksqlDB_host_running || exit 1
sleep 2

# Create ksqlDB tables and streams
printf "${CYAN}Creating ksqlDB Tables and Streams${RESET}\n"
docker cp statements.sql ksqldb-server:/statements.sql
docker exec -it ksqldb-server ksql http://localhost:8088 -f /statements.sql

# Register connectors
# printf "${CYAN}Registering Connectors${RESET}\n"
# CONNECT_REST_API="http://localhost:8083"
# curl -s -X POST -H 'Content-Type: application/json' --data @connector_configs/elasticsearch.json ${CONNECT_REST_API}/connectors

printf "${GREEN}\nDone${RESET}\n"
