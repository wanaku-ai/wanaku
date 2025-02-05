GRAAL_VERSION=21.0.2-graalce

cli-native:
	sdk use java $(GRAAL_VERSION)
	export GRAALVM_HOME=$(JAVA_HOME)
	mvn -Pnative clean package

install:
	mkdir -p $(HOME)/bin
	install -m755 cli/target/cli-1.0-SNAPSHOT-runner $(HOME)/bin/wanaku
	ln -sf $(HOME)/bin/wanaku $(HOME)/bin/wk
