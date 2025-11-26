# Wanaku MCP CLI

## Overview

Command-line interface tool for managing the Wanaku MCP Router via its management API.

## Purpose

The Wanaku CLI provides a user-friendly interface for:
- Managing tools and resources
- Configuring namespaces
- Monitoring capability services
- Creating new capability projects
- Authenticating with the router

## Key Features

- **Tool Management**: Add, list, update, and remove MCP tools
- **Resource Management**: Manage MCP resources and providers
- **Namespace Support**: Organize tools and resources across namespaces
- **Capability Monitoring**: View registered capability services and their health
- **Project Scaffolding**: Generate new tool and provider projects from templates
- **Authentication**: OAuth 2.0/OIDC authentication with the router
- **Label Filtering**: Advanced filtering using label expressions

## Installation

### Via JBang (Recommended)

```shell
jbang app install wanaku@wanaku-ai/wanaku
```

### Via Binary Download

Download the latest release from [GitHub releases](https://github.com/wanaku-ai/wanaku/releases) and extract to your PATH.

## Basic Usage

```shell
# Authenticate with router
wanaku auth login --url http://localhost:8080

# List available tools
wanaku tools list

# Add a new tool
wanaku tools add --uri http://example.com/api --service http

# List resources
wanaku resources list

# View capability services
wanaku capabilities list

# Create a new tool project
wanaku services create tool --name my-tool
```

## Configuration

The CLI stores configuration in `~/.wanaku/`:
- `credentials` - Authentication tokens
- `cli.properties` - CLI configuration

## Related Documentation

- [Usage Guide](../docs/usage.md) - Complete CLI reference
- [Label Expressions Guide](src/main/resources/docs/LABEL_EXPRESSIONS.md) - Advanced filtering
- [Architecture](../docs/architecture.md)