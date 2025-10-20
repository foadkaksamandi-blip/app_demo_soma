#!/bin/bash
# Gradle Wrapper proxy for GitHub Actions (Linux)
GRADLEW_DIR="$(dirname "$0")"
exec ./gradlew "$@"
