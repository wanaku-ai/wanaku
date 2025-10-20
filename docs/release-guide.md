# Wanaku Release Guide

## Automated Release Process

Start by setting the versions. 

```shell
export PREVIOUS_VERSION=0.0.7
export CURRENT_DEVELOPMENT_VERSION=0.0.8
export NEXT_DEVELOPMENT_VERSION=0.0.9
```

Trigger a release build: 

```shell
gh workflow run release -f previousDevelopmentVersion=${PREVIOUS_VERSION} -f currentDevelopmentVersion=${CURRENT_DEVELOPMENT_VERSION} -f nextDevelopmentVersion=${NEXT_DEVELOPMENT_VERSION}
```

The release build can take up to 30 minutes to complete. This will:

1. Validate the artifacts 
2. Publish the files to the Maven Central.

> [IMPORTANT]
> It is **absolutely mandatory** for the artifacts to be validated for the release to proceed.

After the upload is complete, go to [Maven Central](https://central.sonatype.com/publishing/deployments) and publish the deployment.

The publication to Maven Central should take another 30 minutes. 

In the meantime, you can release the artifacts. This will build the zip files and tarballs with each 
component, the native executables and also will publish the containers to Quay.

```
gh workflow run release-artifacts -f currentDevelopmentVersion=$(cat core/core-util/target/classes/version.txt)
```

After this is completed successfully, you can announce the release.

### Early Builds

To release early builds, run:

```shell
gh workflow run early-access -f currentDevelopmentVersion=$(cat core/core-util/target/classes/version.txt)
```

## Manual Release Process

### Prepare the environment 

* Tools required: GraalVM, jreleaser, gpg (with your keys installed and available) and Apache Maven.
* Hardware: Linux (x86 64) and macOS (aarch64)

#### Keys 

Make sure you have your GPG keys installed. You can check with the following command:

```shell
gpg --list-public-keys --keyid-format LONG
```

> [NOTE]
> Make sure the configuration file is stored securely and not accessible by others. 

## Before start 

Repeat this for every machine to be used for the release.

```shell
export PREVIOUS_VERSION=0.0.7
export CURRENT_DEVELOPMENT_VERSION=0.0.8
export NEXT_DEVELOPMENT_VERSION=0.0.9
```

> [NOTE]
> There is no need to add `-SNAPSHOT` to the versions.

### Pre-Release Checks / Dry Run 

> [NOTE]
> The steps assume you are primarily building on macOS, with a secondary step on a x86-64 Linux machine.

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
jreleaser full-release -Djreleaser.project.version=${CURRENT_DEVELOPMENT_VERSION}-SNAPSHOT --select-platform=linux-x86_64 --dry-run --exclude-distribution=cli --exclude-distribution=router --exclude-distribution=service-kafka --exclude-distribution=service-http --exclude-distribution=provider-file --exclude-distribution=service-yaml-route --exclude-distribution=provider-ftp --exclude-distribution=service-sqs --exclude-distribution=service-telegram
```

If everything goes alright, then it should be ready to release.

### Release Maven artifacts

```shell
mvn release:clean
mvn --batch-mode -Dtag=wanaku-${CURRENT_DEVELOPMENT_VERSION} release:prepare -DreleaseVersion=${CURRENT_DEVELOPMENT_VERSION} -DdevelopmentVersion=${NEXT_DEVELOPMENT_VERSION}-SNAPSHOT
```

Adjust the Jbang catalog file: 

```shell
sed -i -e "s/$PREVIOUS_VERSION/$CURRENT_DEVELOPMENT_VERSION/g" jbang-catalog.json
```

Commit the auto-generated UI files and the other version-specific files:

```shell
mvn -PcommitFiles scm:checkin
```

> [NOTE]:
> Do not perform any other manual commit nor push the code. If necessary, append to the UI commit.

Erase the tag created incorrectly by Maven

```shell
git tag -d wanaku-${CURRENT_DEVELOPMENT_VERSION}
```

Recreate the release tag by marking tagging at exactly two commits before HEAD (i.: ignoring the version bumps from maven)

```shell
git tag wanaku-${CURRENT_DEVELOPMENT_VERSION} HEAD~2
```

Push the code to the repository:

```shell
git push upstream wanaku-${CURRENT_DEVELOPMENT_VERSION}
```

Now continue with the release:

```shell
mvn -Pdist release:perform
```

After the upload is complete, go to [Maven Central](https://central.sonatype.com/publishing/deployments) and publish the deployment.

### Native Artifacts

#### Publish the native artifacts for macOS (aarch64)

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

#### Publish the native artifacts for Linux (x86 64)

**NOTE**: this guide assumes the main build was performed on a macOS. 

If you are not running this on the machine where you cut the release, then fetch the tags

```shell
git fetch --all --tags && git checkout git checkout wanaku-${CURRENT_DEVELOPMENT_VERSION}
```

Now, build the native artifacts for macOS (aarch64):

```shell
mvn -Pdist -Dnative clean package
```

Check if all went well with a dry-run:

```shell
jreleaser full-release -Djreleaser.project.version=${CURRENT_DEVELOPMENT_VERSION} --select-platform=linux-x86_64 --exclude-distribution=cli --exclude-distribution=router --exclude-distribution=service-kafka --exclude-distribution=service-http --exclude-distribution=provider-file --exclude-distribution=service-yaml-route --exclude-distribution=provider-ftp --exclude-distribution=service-sqs --exclude-distribution=service-telegram --exclude-distribution=service-exec --exclude-distribution=service-tavily --exclude-distribution=provider-s3 --dry-run
```

Then, run `jreleaser` filtering the source ones, and only publishing the Linux native deliverables on GitHub.

```shell
jreleaser full-release -Djreleaser.project.version=${CURRENT_DEVELOPMENT_VERSION} --select-platform=linux-x86_64 --exclude-distribution=cli --exclude-distribution=router --exclude-distribution=service-kafka --exclude-distribution=service-http --exclude-distribution=provider-file --exclude-distribution=service-yaml-route --exclude-distribution=provider-ftp --exclude-distribution=service-sqs --exclude-distribution=service-telegram --exclude-distribution=service-exec --exclude-distribution=service-tavily --exclude-distribution=provider-s3
```

### Containers

This process is automated, but if there is a need to run it manually, then it can be done using the following:

```shell
mvn -Pdist -Dquarkus.container-image.build=true -Dquarkus.container-image.push=true clean package
```

> [NOTE]:
> You must be logged in in Quay.io with the Podman (preferred) or Docker CLI.