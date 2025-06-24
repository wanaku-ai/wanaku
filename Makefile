GRAAL_VERSION?=21.0.2-graalce
WANAKU_VERSION := $(shell cat core/core-util/target/classes/version.txt)

HOST?=localhost
API_ENDPOINT?=http://$(HOST):8080

mkfile_path := $(abspath $(lastword $(MAKEFILE_LIST)))
mkfile_dir := $(dir $(mkfile_path))
WANAKU_CLI_CMD:= java -jar $(mkfile_dir)/cli/target/quarkus-app/quarkus-run.jar

help:
	@echo Make sure to adjust your environment to use Graal. For instance, run "sdk use ${GRAAL_VERSION}"

prepare:
	@echo sdk use java ${GRAAL_VERSION}

cli-native:
	export GRAALVM_HOME=$(JAVA_HOME)
	mvn -DskipTests -Dnative clean package

dist-native:
	export GRAALVM_HOME=$(JAVA_HOME)
	mvn -Pdist -Dnative clean package

install:
	mkdir -p $(HOME)/bin
	install -m755 cli/target/cli-$(WANAKU_VERSION)-runner $(HOME)/bin/wanaku
	ln -sf $(HOME)/bin/wanaku $(HOME)/bin/wk

load-meta:
	$(WANAKU_CLI_CMD) tools add -n "wanaku-tools-list" --description "List tools available on the Wanaku MCP router" --uri "http://localhost:8080/api/v1/tools/list" --type http
	$(WANAKU_CLI_CMD) tools add -n "wanaku-resources-list" --description "List resources available on the Wanaku MCP router" --uri "http://localhost:8080/api/v1/resources/list" --type http

test-resources:
	$(WANAKU_CLI_CMD) resources expose --host $(API_ENDPOINT) --location=$(mkfile_dir)/tests/data/files/test.txt --mimeType=text/plain --description="Sample resource added via CLI" --name="sample-file" --type=file
	$(WANAKU_CLI_CMD) resources expose --host $(API_ENDPOINT) --location=$(mkfile_dir)./tests/data/files --mimeType=text/plain --description="Sample resource dir added via CLI" --name="sample-dir" --type=file

test-tools:
	$(WANAKU_CLI_CMD) tools add --host $(API_ENDPOINT) -n "meow-facts" --description "Retrieve random facts about cats" --uri "https://meowfacts.herokuapp.com?count={count or 1}" --type http --property "count:int,The count of facts to retrieve" --required count
	$(WANAKU_CLI_CMD) tools add --host $(API_ENDPOINT) -n "dog-facts" --description "Retrieve random facts about dogs" --uri "https://dogapi.dog/api/v2/facts?limit={count or 1}" --type http  --property "count:int,The count of facts to retrieve" --required count
	$(WANAKU_CLI_CMD) tools add --host $(API_ENDPOINT) -n "camel-rider-quote-generator" --description "Generate a random quote from a Camel rider" --uri "file://$(mkfile_dir)/tests/data/routes/camel-route/hello-quote.camel.yaml" --type camel-yaml
	$(WANAKU_CLI_CMD) tools add --host $(API_ENDPOINT) -n "tavily-search" --description "Search on the internet using Tavily" --uri "tavily://search" --type tavily
	$(WANAKU_CLI_CMD) tools add --host $(API_ENDPOINT) -n "laptop-order" --description "Use the request system to order a new laptop" --uri "$(HOME)/.jbang/bin/camel run --max-messages=1 $(mkfile_dir)/tests/data/routes/camel-route/camel-jbang-quote.camel.yaml" --type exec

clean-test-tools:
	$(WANAKU_CLI_CMD) tools remove --name "meow-facts"
	$(WANAKU_CLI_CMD) tools remove --name "dog-facts"
	$(WANAKU_CLI_CMD) tools remove --name "camel-rider-quote-generator"
	$(WANAKU_CLI_CMD) tools remove --name "tavily-search"
	$(WANAKU_CLI_CMD) tools remove --name "laptop-order"

clean-test-resources:
	$(WANAKU_CLI_CMD) resources remove --name "sample-file"
	$(WANAKU_CLI_CMD) resources remove --name "sample-dir"

clean-data: clean-test-resources clean-test-tools
	$(WANAKU_CLI_CMD) resources list
	$(WANAKU_CLI_CMD) tools list

load-test: test-resources test-tools
	$(WANAKU_CLI_CMD) targets resources list
	$(WANAKU_CLI_CMD) targets tools list

refresh-early-builds:
	mvn -Pdist clean package
	jreleaser full-release -Djreleaser.project.version=$(WANAKU_VERSION) --select-platform=osx-aarch_64 --exclude-distribution=cli-native --exclude-distribution=router-native --exclude-distribution=service-kafka-native --exclude-distribution=service-http-native --exclude-distribution=provider-file-native --exclude-distribution=service-yaml-route-native --exclude-distribution=provider-ftp-native --exclude-distribution=service-exec-native --exclude-distribution=provider-s3-native --exclude-distribution=service-sqs-native -Djreleaser.project.snapshot.label="early-access"