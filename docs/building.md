# Building Wanaku MCP Router

## Overview

Before running or deploying the Wanaku MCP Router, you need to build the project from source.

## Prerequisites

* [Apache Maven](https://maven.apache.org) 3.x is required to build and package the project.
* For (experimental) native CLI builds, please follow the [Quarkus instructions on how to set up your environment](https://quarkus.io/guides/building-native-image).

### Building the Project

To build the Wanaku MCP Router, run:

```shell
mvn clean package
```

This command will compile the source code and package it.

To package it into a distributable formats (i.e.: tarballs), run: 

```shell
mvn -Pdist clean package
```

## Building with Native CLI Support

To build the project with native CLI support, run:

```shell
mvn -Pnative clean package
```

## Building with Native Router Support

Wanaku Router also supports native mode, but this feature has not been fully tested.

To enable native mode for the router, run:

```shell
mvn -Pdist -Dnative -Dnative-router clean package
```

> [!IMPORTANT]
> Native support is experimental

## Building with Native Services Support

Wanaku services also supports native mode, but this feature has not been fully tested.

To enable native mode for the router, run:

```shell
mvn -Pdist -Dnative -Dnative-services clean package
```

> [!IMPORTANT]
> Native support is experimental

By following these steps, you should be able to successfully build the Wanaku MCP Router project and prepare it for deployment.

## Native build tips

The project comes with rudimentary automation for native builds for development purposes. For instance, you can install the CLI 
into your `$HOME/bin/` directory by running:

```shell
eval $(make prepare)
make cli-native
make install
```

## Building with the containers

```shell
mvn -Pdist -Dquarkus.container-image.build=true -Dquarkus.container-image.push=true clean package
```