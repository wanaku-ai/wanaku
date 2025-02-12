# Wanaku - A MCP Router that connects everything

The Wanaku MCP Router is a router for AI-enabled applications powered by the [Model Context Protocol (MCP)[https://modelcontextprotocol.io/].

This protocol is an open protocol that standardizes how applications provide context to LLMs. 

The project name comes from the origins of the word [Guanaco](https://en.wikipedia.org/wiki/Guanaco), a camelid native to
South America.

## Installation 

### Using Containers 

To execute the project using containers run: 

```shell
podman run -p 8080:8080 quay.io/megacamelus/wanaku-router
```

NOTE: replace `podman` with `docker` if that's what you have on your environment.

## Usage Guide

Please follow the [usage guide](docs/usage.md) to learn how to use Wanaku.

## Building Wanaku MCP Router

If you want to contribute to the project, the first step is to [learn how to build it](docs/building.md).

