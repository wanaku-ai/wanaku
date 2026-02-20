# Wanaku Core

## Overview

Core library modules providing fundamental functionality for the Wanaku MCP Router ecosystem.

## Purpose

This directory contains essential libraries and infrastructure used throughout Wanaku:
- MCP protocol implementation
- gRPC communication protocols
- Data persistence abstractions
- Service discovery mechanisms
- Base classes for capabilities

## Sub-Modules

### core-mcp
MCP protocol client and server implementations using the Quarkus MCP extension.

### core-exchange
gRPC protocol definitions and message exchange contracts for communication between router and capability services.

### core-persistence
Data persistence abstractions with Infinispan implementation for storing tools, resources, and router state.

### core-capabilities-base
Base classes and utilities for building capability services (tools and resource providers).

### core-service-discovery
Service registration, discovery, and health monitoring mechanisms.

### core-util
Common utilities, constants, and helper classes used across modules.

## Usage

These modules are library dependencies consumed by other Wanaku components. They are not deployed standalone.

## Related Documentation

- [Architecture Overview](../docs/architecture.md)
- [Wanaku Router Internals](../docs/wanaku-router-internals.md)
- [Contributing Guide](../CONTRIBUTING.md)