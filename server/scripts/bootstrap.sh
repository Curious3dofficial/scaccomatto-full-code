#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."
h2_version="2.3.232"
h2_jar="server/lib/h2-${h2_version}.jar"

mkdir -p server/lib

download_jar() {
  local group_path="$1"
  local artifact="$2"
  local version="$3"
  local jar="server/lib/${artifact}-${version}.jar"

  if [[ -f "$jar" ]]; then
    return
  fi

  curl -L --fail \
    "https://repo1.maven.org/maven2/${group_path}/${artifact}/${version}/${artifact}-${version}.jar" \
    -o "$jar"
}

download_jar "com/h2database" "h2" "$h2_version"

# Google OAuth support for the plain javac build.
download_jar "com/google/api-client" "google-api-client" "2.9.0"
download_jar "com/google/oauth-client" "google-oauth-client" "1.36.0"
download_jar "com/google/http-client" "google-http-client" "2.0.0"
download_jar "com/google/http-client" "google-http-client-gson" "2.0.0"
download_jar "com/google/code/gson" "gson" "2.11.0"
download_jar "com/google/guava" "guava" "33.1.0-jre"
download_jar "com/google/guava" "failureaccess" "1.0.2"
download_jar "com/google/code/findbugs" "jsr305" "3.0.2"
