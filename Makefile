GRAAL_VERSION?=21.0.2-graalce
WANAKU_VERSION := $(shell cat core/core-util/target/classes/version.txt)

HOST?=localhost
API_ENDPOINT?=http://$(HOST):8080

mkfile_path := $(abspath $(lastword $(MAKEFILE_LIST)))
mkfile_dir := $(dir $(mkfile_path))

help:
	@echo Make sure to adjust your environment to use Graal. For instance, run "sdk use ${GRAAL_VERSION}"

prepare:
	@echo sdk use java ${GRAAL_VERSION}

cli-native:
	export GRAALVM_HOME=$(JAVA_HOME)
	mvn -Dnative clean package

dist-native:
	export GRAALVM_HOME=$(JAVA_HOME)
	mvn -Pdist -Dnative clean package

install:
	mkdir -p $(HOME)/bin
	install -m755 cli/target/cli-$(WANAKU_VERSION)-runner $(HOME)/bin/wanaku
	ln -sf $(HOME)/bin/wanaku $(HOME)/bin/wk

load-meta:
	wanaku tools add -n "wanaku-tools-list" --description "List tools available on the Wanaku MCP router" --uri "http://localhost:8080/api/v1/tools/list" --type http
	wanaku tools add -n "wanaku-resources-list" --description "List resources available on the Wanaku MCP router" --uri "http://localhost:8080/api/v1/resources/list" --type http

test-resources:
	wanaku resources expose --host $(API_ENDPOINT) --location=$(mkfile_dir)/samples/data/test.txt --mimeType=text/plain --description="Sample resource added via CLI" --name="sample-file" --type=file
	wanaku resources expose --host $(API_ENDPOINT) --location=$(mkfile_dir)./samples/data/ --mimeType=text/plain --description="Sample resource dir added via CLI" --name="sample-dir" --type=file

test-tools:
	wanaku tools add --host $(API_ENDPOINT) -n "meow-facts" --description "Retrieve random facts about cats" --uri "https://meowfacts.herokuapp.com?count={count or 1}" --type http --property "count:int,The count of facts to retrieve" --required count
	wanaku tools add --host $(API_ENDPOINT) -n "dog-facts" --description "Retrieve random facts about dogs" --uri "https://dogapi.dog/api/v2/facts?limit={count or 1}" --type http  --property "count:int,The count of facts to retrieve" --required count
	wanaku tools add --host $(API_ENDPOINT) -n "camel-rider-quote-generator" --description "Generate a random quote from a Camel rider" --uri "file://$(mkfile_dir)/samples/routes/camel-route/hello-quote.camel.yaml" --type camel-yaml --property "wanaku_body:string,the data to be passed to the route"
	wanaku tools add --host $(API_ENDPOINT) -n "tavily-search" --description "Search on the internet using Tavily" --uri "tavily://search" --type tavily --property "wanaku_body:string,The search terms" --property "maxResults:int,The maxResults is the expected number of results to be found if the search request were made" --required "wanaku_body"
	wanaku tools add --host $(API_ENDPOINT) -n "laptop-order" --description "Issue a new laptop order" --uri "$(HOME)/.jbang/bin/camel run --max-messages=1 $(mkfile_dir)/samples/routes/camel-route/camel-jbang-quote.camel.yaml" --type exec

test-targets:
	wanaku targets tools link --host $(API_ENDPOINT) --service=http --target=$(HOST):9000
	wanaku targets tools link --host $(API_ENDPOINT) --service=camel-route --target=$(HOST):9001
	wanaku targets tools link --host $(API_ENDPOINT) --service=kafka --target=$(HOST):9003
	wanaku targets resources link --host $(API_ENDPOINT) --service=file --target=$(HOST):9002

clean-test-tools:
	wanaku tools remove --name "meow-facts"
	wanaku tools remove --name "dog-facts"
	wanaku tools remove --name "camel-rider-quote-generator"
	wanaku tools remove --name "tavily-search"
	wanaku tools remove --name "laptop-order"

clean-test-resources:
	wanaku resources remove --name "sample-file"
	wanaku resources remove --name "sample-dir"

clean-targets:
	wanaku targets tools unlink --host $(API_ENDPOINT) --service=http
	wanaku targets tools unlink --host $(API_ENDPOINT) --service=camel-route
	wanaku targets tools unlink --host $(API_ENDPOINT) --service=kafka
	wanaku targets resources unlink --host $(API_ENDPOINT) --service=file
	wanaku targets resources list
	wanaku targets tools list

clean-data: clean-test-resources clean-test-tools
	wanaku resources list
	wanaku tools list

load-test: test-resources test-tools test-targets
	wanaku targets resources list
	wanaku targets tools list

refresh-early-builds:
	mvn -Pdist clean package
	jreleaser full-release -Djreleaser.project.version=$(WANAKU_VERSION) --select-platform=osx-aarch_64 --exclude-distribution=cli-native --exclude-distribution=router-native --exclude-distribution=service-kafka-native --exclude-distribution=service-http-native --exclude-distribution=provider-file-native --exclude-distribution=service-yaml-route-native --exclude-distribution=provider-ftp-native --exclude-distribution=service-exec-native --exclude-distribution=provider-s3-native -Djreleaser.project.snapshot.label="early-access"