#!/bin/bash
# Installs Tigase from Kontalk custom repositories

current_branch() {
  cd $(dirname $0)
  branch=$(git symbolic-ref -q HEAD)
  echo ${branch##refs/heads/}
}

BRANCH="$1"
if [ "$BRANCH" == "" ]; then
  BRANCH=$(current_branch)
fi
echo "On branch ${BRANCH}"

REPOS="gnupg-for-java tigase-utils"

for REPO in ${REPOS}; do
  echo "Building ${REPO}"
  git clone -b "${BRANCH}" "https://github.com/kontalk/${REPO}.git" &&
  cd "${REPO}" &&
  mvn install &&
  cd .. &&
  rm -fR "${REPO}" ||
  exit $?
done

REPO="tigase-server"
echo "Building ${REPO}"
git clone -b "${BRANCH}" "https://github.com/kontalk/${REPO}.git" &&
cd "${REPO}/modules/master" &&
mvn install &&
cd ../.. &&
mvn install
cd .. &&
rm -fR "${REPO}" ||
exit $?
