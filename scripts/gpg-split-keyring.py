#!/usr/bin/env python
# Splits a GPG keyring into multiple keyrings
# usage: ./gpg-split-keyring.sh <input_home> <output_base> <partitions>

import gpgme
import gpgme.editutil

try:
    from io import BytesIO
except ImportError:
    from StringIO import StringIO as BytesIO

import sys
import os

INPUT_HOME = sys.argv[1]
OUTPUT_BASE = sys.argv[2]
PARTITIONS = int(sys.argv[3])

# gpgme input context
ctx_in = gpgme.Context()
ctx_in.set_engine_info(0, None, INPUT_HOME)
ctx_in.armor = False

success = 0
failure = 0

# gpgme output contexts
ctx_out = []
for i in range(PARTITIONS):
    path = OUTPUT_BASE + str(i)
    ctx = gpgme.Context()
    ctx.set_engine_info(0, None, path)
    ctx.armor = False
    ctx_out.append(ctx)
    # create dir
    try:
        os.mkdir(path)
    except OSError:
        pass

keys = ctx_in.keylist(None, False)
keylist = [key.subkeys[0].fpr for key in keys]
for fpr in keylist:
    index = int(fpr[0], 16)
    print "Moving key %s into keyring %d" % (fpr, index)
    keydata = BytesIO()
    ctx_in.export(fpr, keydata)
    keydata = BytesIO(keydata.getvalue())
    result = ctx_out[index].import_(keydata)
    if result and (result.imported == 1 or result.unchanged == 1):
        success += 1
    else:
        failure += 1

print "Success: %d" % success
print "Failure: %d" % failure
