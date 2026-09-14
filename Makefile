.PHONY: ui build build-image build-image-headless clean

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

clean:
	cargo clean
	rm -rf ui/admin/dist ui/admin/node_modules
