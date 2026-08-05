.PHONY: ui build clean

ui:
	cd ui/admin && yarn install && yarn build

build: ui
	cargo build --release -p wanaku-praxis-proxy

clean:
	cargo clean
	rm -rf ui/admin/dist ui/admin/node_modules
