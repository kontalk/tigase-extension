#!/bin/bash
# Installs Tigase from Kontalk custom repositories

REPOS="gnupg-for-java tigase-utils"

for REPO in ${REPOS}; do
  echo "Building ${REPO}"
  git clone "https://github.com/kontalk/${REPO}.git" &&
  cd "${REPO}" &&
  mvn install &&
  cd .. &&
  rm -fR "${REPO}" ||
  exit $?
done

REPO="tigase-server"
echo "Building ${REPO}"
git clone "https://github.com/kontalk/${REPO}.git" &&
cd "${REPO}/modules/master" &&
mvn install &&
cd ../.. &&
mvn install
cd .. &&
rm -fR "${REPO}" ||
exit $?
