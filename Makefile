GRAAL_VERSION?=21.0.2-graalce

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
