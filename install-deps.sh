#!/bin/bash
# Installs Tigase from Kontalk custom repositories

REPOS="gnupg-for-java tigase-utils tigase-server"

for REPO in ${REPOS}; do
  echo "Building ${REPO}"
  git clone "https://github.com/kontalk/${REPO}lol.git" &&
  cd "${REPO}" &&
  mvn install &&
  rm -fR "${REPO}" ||
  exit $?
done
