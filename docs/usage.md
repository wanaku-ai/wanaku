# Using the Wanaku MCP Router CLI

## Overview

The Wanaku MCP Router CLI provides a simple way to manage resources and tools for your Wanaku MCP Router instance.

## Ways to run the CLI

There are three ways to run the CLI. Choose the one that fits your the best: 

1. Using the container: `podman run quay.io/megacamelus/cli`. This is the recommended way.
2. Using the `wanaku` launcher script from the tarball generated during the build
3. Using the `wanaku` native binary generated during the build.

*NOTE*: the commands below will use `wanaku` as the alias representing any of the above options.

## Supported Commands

The following commands are currently supported by the Wanaku MCP Router CLI:

### List Resources

* **Command**: `wanaku resources list`
* **Description**: Lists available resources exposed by the Wanaku MCP Router instance.
* **Usage**: Run this command to view a list of available resources, including their names and descriptions.

```markdown
wanaku resources list
```

### Expose Resource

* **Command**: `wanaku resources expose <resource-id>`
* **Description**: Exposes an existing resource to the Wanaku MCP Router instance.
* **Usage**: Replace `<resource-id>` with the ID of the resource you want to expose. For example:

#### Example

Suppose you have a file named `test-mcp-2.txt` on your home directory, and you want to expose it. 
This is how you can do it:

```shell
wanaku resources expose --location=$HOME/test-mcp-2.txt --mimeType=text/plain --description="Sample resource added via CLI" --name="test mcp via CLI" --type=file
```

### List Tools

* **Command**: `wanaku tools list`
* **Description**: Lists available tools on the Wanaku MCP Router instance.
* **Usage**: Run this command to view a list of available tools, including their names and descriptions.

```markdown
wanaku tools list
```

#### Example

```shell
Name               Type               URI
meow-facts      => http            => https://meowfacts.herokuapp.com?count={count}
dog-facts       => http            => https://dogapi.dog/api/v2/facts?limit={count}
```

### Add Tool

* **Command**: `wanaku tools add ${parameters}`
* **Description**: Adds an existing tool to the Wanaku MCP Router instance.
* **Usage**: Replace `<tool-id>` with the ID of the tool you want to add, and `<tool-credentials>` with the credentials for that tool. For example:

#### Example

Here's how you could add a new tool to a Wanaku MCP router instance running locally on http://localhost:8080:

```shell
wanaku tools add -n "meow-facts" --description "Retrieve random facts about cats" --uri "https://meowfacts.herokuapp.com?count={count}" --type http --property "count:int,The count of facts to retrieve" --required count
```

NOTE: For remote instances, you can use the parameter `--host` to point to the location of the instance.

### API Note

All CLI commands use the Wanaku management API under the hood. If you need more advanced functionality or want to automate tasks, you may be able to use this API directly.

By using these CLI commands, you can manage resources and tools for your Wanaku MCP Router instance.

