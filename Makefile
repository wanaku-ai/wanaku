.PHONY: ui build build-image build-image-headless saci-image clean

TARGET ?= docker

ui:
	cd ui/admin && yarn install && yarn build

build: ui
	cargo build --release -p wanaku-server

## Build the full container image.
## TARGET controls the build environment: docker (default), minikube, openshift.
## Example: make build-image TARGET=minikube
build-image:
	cargo build-image --target $(TARGET)

## Build the headless container image (no embedded admin UI).
## Example: make build-image-headless TARGET=openshift
build-image-headless:
	cargo build-image-headless --target $(TARGET)

RUNTIME ?= $(shell command -v podman >/dev/null 2>&1 && echo podman || echo docker)
SACI_BASE ?= quay.io/orpiske/saci-worker:latest
SACI_TAG ?= localhost/wanaku-saci-worker:latest

## Build the Saci worker image with wanaku-server and the wanaku CLI.
## See tests/saci/README.md. Example: make saci-image RUNTIME=docker
saci-image:
	$(RUNTIME) build -f Containerfile -t localhost/wanaku-server:saci .
	$(RUNTIME) build -f tests/saci/Containerfile \
		--build-arg SACI_IMAGE=$(SACI_BASE) \
		--build-arg WANAKU_IMAGE=localhost/wanaku-server:saci \
		-t $(SACI_TAG) tests/saci

clean-ui:
	rm -rf ui/admin/dist ui/admin/node_modules

clean: clean-ui
	cargo clean
