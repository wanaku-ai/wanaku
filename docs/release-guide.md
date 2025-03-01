# Wanaku Release Guide

## Prepare the environment 

* Tools required: GraalVM, jreleaser, gpg (with your keys installed and available) and Apache Maven.
* Hardware: Linux (x86 64) and macOS (aarch64)

### Keys 

Make sure you have your GPG keys installed. You can check with the following command:

```shell
gpg --list-public-keys --keyid-format LONG
```

**NOTE**: make sure the configuration file is stored securely and not accessible by others. 

## Before start 

Repeat this for every machine to be used for the release.

```shell
export CURRENT_DEVELOPMENT_VERSION=0.0.1
export NEXT_DEVELOPMENT_VERSION=0.0.2
```

**NOTE**: there is no need to add `-SNAPSHOT` to the versions.

## Pre-Release Checks / Dry Run 

**NOTE**: The steps assume you are primarily building on macOS, with a secondary step on a x86-64 Linux machine.

Build the project

```shell
mvn -Pdist -Dnative clean package
```

Then, check if the build can be released. 

**NOTE**: make sure to replace the version with the actual version you are building.

```shell
jreleaser full-release -Djreleaser.project.version=${CURRENT_DEVELOPMENT_VERSION}-SNAPSHOT --select-platform=osx-aarch_64 --dry-run
```

Then, on your Linux host, build the project again and check if Linux artifacts can be released.

```shell
jreleaser full-release -Djreleaser.project.version=${CURRENT_DEVELOPMENT_VERSION}-SNAPSHOT --select-platform=linux-x86_64 --exclude-distribution=cli --exclude-distribution=router --exclude-distribution=service-kafka --exclude-distribution=service-http --exclude-distribution=provider-file --exclude-distribution=service-yaml-route --exclude-distribution=provider-ftp -Djreleaser.project.snapshot.label="early-access-macos"
```

If everything goes alright, then it should be ready to release.

## Release Maven artifacts

```shell
mvn release:clean
mvn --batch-mode -Dtag=wanaku-${CURRENT_DEVELOPMENT_VERSION} release:prepare -DreleaseVersion=${CURRENT_DEVELOPMENT_VERSION} -DdevelopmentVersion=${NEXT_DEVELOPMENT_VERSION}-SNAPSHOT
mvn -Pdist release:perform
```

After the upload is complete, go to [Maven Central](https://central.sonatype.com/publishing/deployments) and publish the deployment.

## Native Artifacts

### Publish the native artifacts for macOS (aarch64)

Now, build the native artifacts for macOS (aarch64) and publish them on GitHub.

```shell
mvn -Pdist -Dnative clean package
```

Perform a dry-run to check if everything is OK:
```shell
jreleaser full-release -Djreleaser.project.version=${CURRENT_DEVELOPMENT_VERSION} --select-platform=osx-aarch_64 --dry-run
```

If everything is OK, then publish:

```shell
jreleaser full-release -Djreleaser.project.version=${CURRENT_DEVELOPMENT_VERSION} --select-platform=osx-aarch_64
```

### Publish the native artifacts for Linux (x86 64)

**NOTE**: this guide assumes the main build was performed on a macOS. 

If you are not running this on the machine where you cut the release, then fetch the tags

```shell
git fetch --all --tags
```

Now, build the native artifacts for macOS (aarch64):

```shell
mvn -Pdist -Dnative clean package
```

Check if all went well with a dry-run:

```shell
jreleaser full-release -Djreleaser.project.version${CURRENT_DEVELOPMENT_VERSION} --select-platform=linux-x86_64 --exclude-distribution=cli --exclude-distribution=router --exclude-distribution=service-kafka --exclude-distribution=service-http --exclude-distribution=provider-file --exclude-distribution=service-yaml-route --exclude-distribution=provider-ftp --dry-run
```

Then, run `jreleaser` filtering the source ones, and only publishing the Linux native deliverables on GitHub.

```shell
jreleaser full-release -Djreleaser.project.version=${CURRENT_DEVELOPMENT_VERSION} --select-platform=linux-x86_64 --exclude-distribution=cli --exclude-distribution=router --exclude-distribution=service-kafka --exclude-distribution=service-http --exclude-distribution=provider-file --exclude-distribution=service-yaml-route --exclude-distribution=provider-ftp 
```

## Containers

This process is automated, but if there is a need to run it manually, then it can be done using the following:

```shell
mvn -Pdist -Dquarkus.container-image.build=true -Dquarkus.container-image.push=true clean package
```

**NOTE**: you must be logged in in Quay.io with the Podman (preferred) or Docker CLI.