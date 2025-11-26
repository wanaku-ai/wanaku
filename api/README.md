# Wanaku API

## Overview

Common API module providing shared data models and interfaces used across the Wanaku ecosystem.

## Purpose

This module defines the standardized API contracts used for communication between:
- CLI and router backend
- Router backend and capability services
- Internal router components

## Key Features

- Common data models for tools, resources, and namespaces
- Service registration and discovery interfaces
- Configuration and provisioning data structures
- Shared utility classes and constants

## Components

- **Data Models**: POJOs representing tools, resources, capabilities, and namespaces
- **API Interfaces**: Contract definitions for service communication
- **DTOs**: Data transfer objects for API requests and responses

## Usage

This is a library module consumed by other Wanaku components. It is not deployed standalone.

### Maven Dependency

```xml
<dependency>
    <groupId>ai.wanaku</groupId>
    <artifactId>api</artifactId>
    <version>${wanaku.version}</version>
</dependency>
```

## Related Documentation

- [Architecture Overview](../docs/architecture.md)
- [Contributing Guide](../CONTRIBUTING.md)