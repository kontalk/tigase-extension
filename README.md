Kontalk XMPP Tigase extension
=============================

[![Build Status](https://travis-ci.org/kontalk/tigase-extension.svg?branch=master)](https://travis-ci.org/kontalk/tigase-extension)

This repository contains a set of custom extensions and plugins for the Tigase XMPP server.
They are based on latest git master of our custom patched [tigase-server](//github.com/kontalk/tigase-server), which depends on [tigase-utils](//github.com/kontalk/tigase-utils).

## Dependencies

Internal dependencies can be automatically installed by running the script `install-deps.sh`. It will download all repositories in the current working directory and run `mvn install` for each one of them.

* [tigase-server](//github.com/kontalk/tigase-server)
* [tigase-utils](//github.com/kontalk/tigase-utils)
* [gnupg-for-java](//github.com/kontalk/gnupg-for-java)

## Build

```
mvn install
```

The above command will install everything in your local Maven repository.

## Install

Please refer to the wiki for further installation instruction.
