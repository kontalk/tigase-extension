#!/bin/bash
# Migration tool from GnuPG keyring to Kyoto Cabinet keyring

SRC="$1"
DST="$2"

GPG_OPTS="--no-default-keyring --keyring $(realpath ${SRC})"

# create our kyoto cabinet
kchashmgr create ${DST}

for FPR in `gpg2 --with-colons --with-fingerprint --list-public-keys ${GPG_OPTS} | grep fpr | awk '{print $10}' FS=:`; do
#echo ${FPR}
KEY=$(gpg2 ${GPG_OPTS} --export ${FPR} | xxd -p | tr -d '\n')
kchashmgr set -rep -sx ${DST} ${FPR} ${KEY} ||
kchashmgr set -add -sx ${DST} ${FPR} ${KEY}
done
