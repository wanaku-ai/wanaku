GRAAL_VERSION?=21.0.2-graalce

HOST?=localhost
API_ENDPOINT?=http://$(HOST):8080

help:
	@echo Make sure to adjust your environment to use Graal. For instance, run "sdk use ${GRAAL_VERSION}"

prepare:
	@echo sdk use java ${GRAAL_VERSION}

cli-native:
	export GRAALVM_HOME=$(JAVA_HOME)
	mvn -Pnative clean package

install:
	mkdir -p $(HOME)/bin
	install -m755 cli/target/cli-1.0.0-SNAPSHOT-runner $(HOME)/bin/wanaku
	ln -sf $(HOME)/bin/wanaku $(HOME)/bin/wk

load-meta:
	wanaku tools add -n "wanaku-tools-list" --description "List tools available on the Wanaku MCP router" --uri "http://localhost:8080/api/v1/tools/list" --type http
	wanaku tools add -n "wanaku-resources-list" --description "List resources available on the Wanaku MCP router" --uri "http://localhost:8080/api/v1/resources/list" --type http

test-resources:
	wanaku resources expose --host $(API_ENDPOINT) --location=./samples/data/test.txt --mimeType=text/plain --description="Sample resource added via CLI" --name="sample-file" --type=file

test-tools:
	wanaku tools add --host $(API_ENDPOINT) -n "meow-facts" --description "Retrieve random facts about cats" --uri "https://meowfacts.herokuapp.com?count={count}" --type http --property "count:int,The count of facts to retrieve" --required count
	wanaku tools add --host $(API_ENDPOINT) -n "dog-facts" --description "Retrieve random facts about dogs" --uri "https://dogapi.dog/api/v2/facts?limit={count}" --type http  --property "count:int,The count of facts to retrieve" --required count
	wanaku tools add --host $(API_ENDPOINT) -n "hello-camel-generator" --description "Generate a random quote from a Camel rider" --uri "camel://$(HOME)/code/java/wanaku/samples/routes/camel-route/hello-quote.camel.yaml" --type camel-route --property "_body:string,the data to be passed to the route"

test-targets:
	wanaku targets tools link --host $(API_ENDPOINT) --service=http --target=$(HOST):9000
	wanaku targets tools link --host $(API_ENDPOINT) --service=camel-route --target=$(HOST):9001
	wanaku targets tools link --host $(API_ENDPOINT) --service=kafka --target=$(HOST):9003
	wanaku targets resources link --host $(API_ENDPOINT) --service=file --target=$(HOST):9002

clean-test-tools:
	wanaku tools remove -n "meow-facts"
	wanaku tools remove -n "dog-facts"
	wanaku tools remove -n "hello-camel-generator"

clean-test-resources:
	wanaku resources remove -n "sample-file"

clean-targets:
	wanaku targets tools unlink --host $(API_ENDPOINT) --service=http
	wanaku targets tools unlink --host $(API_ENDPOINT) --service=camel-route
	wanaku targets tools unlink --host $(API_ENDPOINT) --service=kafka
	wanaku targets resources unlink --host $(API_ENDPOINT) --service=file

clean-data: clean-test-resources clean-test-tools
	wanaku targets resource list
	wanaku targets tools list

load-test: test-resources test-tools test-targets
	wanaku targets resource list
	wanaku targets tools list