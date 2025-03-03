# Using the Wanaku MCP Router

Wanaku aims to provide unified access, routing and resource management capabilities for your organization and your AI Agents.

## Meet Wanaku 

If you haven't seen it already, we recommend watching the Getting Started with Wanaku video that introduces the project, 
and introduces how it works.

[![Getting Started With Wanaku](https://img.youtube.com/vi/-fuNAo2j4SA/0.jpg)](https://www.youtube.com/watch?v=-fuNAo2j4SA)

## Overview

In addition to installing the Wanaku MCP Router, it is also necessary to install the CLI used to manage the router. 
The Wanaku MCP Router CLI provides a simple way to manage resources and tools for your Wanaku MCP Router instance.

**NOTE**: Wanaku also comes with a web user interface that you can access on port 8080 of the host running the router, but at 
this moment, some features are only available on the CLI. 

The MCP endpoint exposed by Wanaku can be accessed on the path `/mcp/sse` of the host your are using (for instance, if running 
locally, that would mean `http://localhost:8080/mcp/sse`)

## Getting the CLI

The best way to install the CLI is by downloading the latest `cli` from the [latest release](https://github.com/wanaku-ai/wanaku/releases).

*NOTE*: You may also find a container for the CLI on our [Quay.io organization](https://quay.io/repository/wanaku/cli), 
although it is not entirely tested at the moment. 

## Quick Getting Started 

Wanaku needs providers and tools to serve and route. These are the downstream services that Wanaku talk to. 

The first step is to launch them. 

Get the [`docker-compose.yml`](https://raw.githubusercontent.com/wanaku-ai/wanaku/refs/heads/main/docker-compose.yml):

```shell
wget https://raw.githubusercontent.com/wanaku-ai/wanaku/refs/heads/main/docker-compose.yml
```

Then, you can launch the containers using:

```shell
docker-compose up -d
```

With the Wanaku MCP router up and running, then, link the tools and providers to the router:

```shell
wanaku targets tools link --service=http --target=host.docker.internal:9000
wanaku targets tools link --service=camel-route --target=host.docker.internal:9001
wanaku targets resources link --service=file --target=host.docker.internal:9002
```

After the tools and resource providers have been linked with the router, then you can 
start adding tools and resources to be served via MCP using the commands described below.

### Importing a ToolSet

Wanaku ToolSets are a group of tools that you can use to share with friends and colleagues and 
can be easily imported into the router. 
Wanaku [comes with a couple of ToolSets](https://github.com/wanaku-ai/wanaku-toolsets) that you can import into your router and use them to try 
it and see how it works.

The first step is to download a toolset: 

```shell
wget -c https://raw.githubusercontent.com/wanaku-ai/wanaku-toolsets/refs/heads/main/toolsets/currency.json
```

Then, you can import them into the router using: 

```shell
wanaku tools import /path/to/currency.json
```

You can check if they were imported by running the following command: 

```shell
wanaku tools list
```

## Supported Commands

The following commands are currently supported by the Wanaku MCP Router CLI:

### List Resources

Lists available resources exposed by the Wanaku MCP Router instance. 
Run this command to view a list of available resources, including their names and descriptions.

```shell
wanaku resources list
```

### Expose Resource

Exposes an existing resource to the Wanaku MCP Router instance.

#### Example

Suppose you have a file named `test-mcp-2.txt` on your home directory, and you want to expose it. 
This is how you can do it:

```shell
wanaku resources expose --location=$HOME/test-mcp-2.txt --mimeType=text/plain --description="Sample resource added via CLI" --name="test mcp via CLI" --type=file
```

### List Tools

Lists available tools on the Wanaku MCP Router instance. 
Run this command to view a list of available tools, including their names and descriptions.

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

Adds an existing tool to the Wanaku MCP Router instance.

#### Example

Here's how you could add a new tool to a Wanaku MCP router instance running locally on http://localhost:8080:

```shell
wanaku tools add -n "meow-facts" --description "Retrieve random facts about cats" --uri "https://meowfacts.herokuapp.com?count={count}" --type http --property "count:int,The count of facts to retrieve" --required count
```

NOTE: For remote instances, you can use the parameter `--host` to point to the location of the instance.

### Targets 

Link a service provider hosted at a specific address to handle tools of a specific type.

```shell
wanaku targets tools link --service=http --target=host.docker.internal:9000
```

On the command above, it would cause all tools of type `http` to be handled by the provider available on `host.docker.internal:9000`.

#### Configuring Targets 

Some tools services may need to be configured before they can be run. For instance, the Kafka tool needs
to know both the address of the broker and the topic where to wait for a reply.

```shell
wanaku targets tools configure --service=kafka --option=bootstrapHost --value=my-kafka-host:9092
```

And, then to configure the reply to topic:

```shell
wanaku targets tools configure --service=kafka --option=replyToTopic --value=someTopicToWaitFor.reply
```

#### Targets List 

You can view linked targets using the `targets tools list` or the `target resources list` command. 

```shell
wanaku targets tools list
Service                 Target                            Configurations
kafka                => localhost:9003                 => bootstrapHost, replyToTopic
http                 => localhost:9000                 =>
```


### API Note

All CLI commands use the Wanaku management API under the hood. If you need more advanced functionality or want to automate tasks, you may be able to use this API directly.

By using these CLI commands, you can manage resources and tools for your Wanaku MCP Router instance.

## Tools

### Running Camel Routes as Tools

You can design the routes visually, using [Kaoto](https://kaoto.io/). You need to make sure that the start endpoint for the 
route is `direct:start`. If in doubt, check the [hello-quote.camel.yaml](../samples/routes/camel-route/hello-quote.camel.yaml)
file in the `samples` directory.

To add that route as a tool, you can run something similar to this: 

```shell
wanaku tools add -n "camel-rider-quote-generator" --description "Generate a random quote from a Camel rider" --uri "file:///$(HOME)/code/java/wanaku/samples/routes/camel-route/hello-quote.camel.yaml" --type camel-route --property "_body:string,The data to be passed to the route"
```

## Supported/Tested Client 

Wanaku implements the MCP protocol and, by definition, should support any client that is compliant to the protocol. 

The details below describe how Wanaku MCP router can be used with some prominent MCP clients: 

### LibreChat

For [LibreChat](https://www.librechat.ai/docs) search for `mcpServers` on the `librechat.yml` file and include something similar to this:
   
```
mcpServers:
    everything:
        url: http://host.docker.internal:8080/mcp/sse
```

**NOTE**: make sure to point to the correct address of your Wanaku MCP instance.

In LibreChat, you can access Wanaku MCP tools using [Agents](https://www.librechat.ai/docs/features/agents).

## Available Resources Providers 

The following resources can be made available using Wanaku.

| Type   | Resource Provider    | Description                                     |
|--------|----------------------|-------------------------------------------------|
| `file` | wanaku-provider-file | Provides access to files as resources to Wanaku |

## Available Tools Services

The following tools services can be made available using Wanaku and used to provide access to specific services.

| Type         | Service Tool                      | Description                                              |
|--------------|-----------------------------------|----------------------------------------------------------|
| `http`       | wanaku-routing-http-service       | Provides access to HTTP endpoints as tools via Wanaku    |
| `yaml-route` | wanaku-routing-yaml-route-service | Provides access to Camel routes in YAML tools via Wanaku |
| `kafka`      | wanaku-routing-kafka-service      | Provides access to Kafka topics as tools via Wanaku      |
