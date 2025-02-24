# Wanaku MCP Router Components and Architecture

## Overview

The Wanaku MCP Router is a powerful tool for managing Model Context Protocol (MCP) workloads, providing a flexible and extensible
framework for integrating with various servers and tools.

## Components

The Wanaku MCP Router is composed of the following components:

* A web server with MCP capabilities built on top of the [Quarkus MCP Server](https://github.com/quarkiverse/quarkus-mcp-server/)
* A routing engine that receives MCP requests and forwards them to the appropriate provider or service using gGRPC
* A set of service providers capable of reading resources from different systems 
* A set of service tools capable of interconnecting with any of the more than 300 components supported by [Apache Camel](https://camel.apache.org)
* Auxiliary tools and APIs to manage and/or extend the MCP router.