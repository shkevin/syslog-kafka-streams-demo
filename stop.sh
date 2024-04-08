#!/bin/bash
: "
    Script Name:
    Description:
    Args       :
    Author     : Kevin Cox
    Email      :
  "

set -u # Unbouded variables
set -e # Exit if error

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

GREEN='\033[0;32m'
RED='\033[0;31m'
WHITE='\033[0;37m'
CYAN='\033[0;36m'
RESET='\033[0m'

docker compose down --volumes

printf "${GREEN}Done${RESET}\n"
