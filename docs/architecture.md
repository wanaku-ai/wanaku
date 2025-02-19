# Wanaku MCP Router Components and Architecture

## Overview

The Wanaku MCP Router is a powerful tool for managing Model Context Protocol (MCP) workloads, providing a flexible and extensible
framework for integrating with various servers and tools.

## Components

The Wanaku MCP Router is composed of the following components:

### CLI


* **Purpose**: Manages the server via the management API.
* **Description**: The Command-Line Interface (CLI) provides a simple way to interact with the Wanaku MCP Router, allowing users to perform tasks such as listing and exposing resources, and listing and adding tools.

### API


* **Purpose**: Common API used globally (including the CLI and router).
* **Description**: The API is a component of the Wanaku MCP Router, providing a standardized interface for interacting with various servers and tools. This API is used by both the CLI and the router.

### Core

* **Purpose**: Core library (support for reading/writing indexes, etc.)
* **Description**: The core library provides essential functionality for managing indexes, reading data from storage, and performing other critical operations.

### Servers

* **Purpose**: Reside supported transports.
* **Description**: This component contains the supported transports for communication.

#### Wanaku-Server-Quarkus

* **Purpose**: Our own implementation of the MCP protocol using Quarkus (served via HTTP+SSE) and containing the management API (REST).
* **Description**: This component provides a custom implementation of the MCP server, leveraging the Quarkus framework to serve HTTP requests and interact with the management API.

#### Routers

* **Purpose**: Reside supported routers (using transport to serve MCP workloads).
* **Description**: This component contains supported router implementations that utilize the transport to serve MCP workloads. Each router provides a unique way to process and forward protocol messages.

#### Wanaku-Router

* **Purpose**: The actual Wanaku MCP router (bridging server capabilities with features provided by Apache Camel).
* **Description**: This component provides a custom and robust implementation of the Wanaku MCP, bridging the capabilities of the Wanaku Server with the advanced features offered by Apache Camel.

#### Services

* **Purpose**: Services that can expose capabilities to the Wanaku MCP router
* **Description**: This component provides a set of components that can expose capabilities (resources, tools, etc.) to the Wanaku MCP router. 
   * **providers**: allow access to resources.
   * **tools**: anything that can be exposed as a tool.

### Artwork, Samples, and Docs

* **Purpose**: Provide non-code related artifacts (samples and documentation) for other team members to benefit from.
* **Description**: This component contains various samples and documentation that assist developers and users in utilizing the Wanaku MCP Router.