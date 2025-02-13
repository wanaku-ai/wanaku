GRAAL_VERSION?=21.0.2-graalce

HOST?="localhost:8080"

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


load-facts:
	wk tools add --host $(HOST) -n "meow-facts" --description "Retrieve random facts about cats" --uri "https://meowfacts.herokuapp.com?count={count}" --type http --property "count:int,The count of facts to retrieve" --required count
	wk tools add --host $(HOST) -n "dog-facts" --description "Retrieve random facts about dogs" --uri "https://dogapi.dog/api/v2/facts?limit={count}" --type http  --property "count:int,The count of facts to retrieve" --required count

load-meta:
	wanaku tools add -n "wanaku-tools-list" --description "List tools available on the Wanaku MCP router" --uri "http://localhost:8080/api/v1/tools/list" --type http
	wanaku tools add -n "wanaku-resources-list" --description "List resources available on the Wanaku MCP router" --uri "http://localhost:8080/api/v1/resources/list" --type http

load-route:
	wanaku tools add -n "hello-camel-generator" --description "Generate a random quote from a Camel rider" --uri "file:///$(HOME)/code/java/wanaku/samples/routes/camel-route/hello-quote.camel.yaml" --type camel-route --property "_body:string,The data to be passed to the route"