#!/bin/sh
set -eu
case "${APP_ROLE:-api}" in api|mcp|ingestion|ai|communication) ;; *) echo 'Invalid APP_ROLE' >&2; exit 1;; esac
exec java -XX:MaxRAMPercentage=60 -XX:InitialRAMPercentage=20 -XX:+ExitOnOutOfMemoryError -Djava.awt.headless=true ${JAVA_OPTS:-} -jar "/app/${APP_ROLE:-api}.jar"
