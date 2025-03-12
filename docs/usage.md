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

### Importing a ToolSet

Wanaku ToolSets are a group of tools that you can use to share with friends and colleagues and 
can be easily imported into the router. 
Wanaku [comes with a couple of ToolSets](https://github.com/wanaku-ai/wanaku-toolsets) that you can import into your router and use them to try 
it and see how it works.

The first step is to import toolset into the router using:

```shell
wanaku tools import https://raw.githubusercontent.com/wanaku-ai/wanaku-toolsets/refs/heads/main/toolsets/currency.json
```

if you already have a toolset definition on your local machine you can import it using:

```shell
wanaku tools import /path/to/the/toolsets/currency.json
```


Now you can check if they were imported by running the following command: 

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

#### Configuring Targets 

Tools services may need to be configured before they can be run. For instance, the Kafka tool needs
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


#### ToolSets

To add a tool to a toolset: 

```shell
wanaku toolset add ./path/to/toolset-file.json -n "meow-facts" --description "Retrieve random facts about cats" --uri "https://meowfacts.herokuapp.com?count={count}" --type http --property "count:int,The count of facts to retrieve" --required count
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

### HyperChat 

Wanaku works with [HyperChat](https://github.com/BigSweetPotatoStudio/HyperChat), however, setting up requires manually editing the `mcp.json` file. You can follow
[the steps descripted on the improvement ticket](https://github.com/BigSweetPotatoStudio/HyperChat/issues/30) to set it up.

### LibreChat

For [LibreChat](https://www.librechat.ai/docs) search for `mcpServers` on the `librechat.yml` file and include something similar to this:
   
```
mcpServers:
    everything:
        url: http://host.docker.internal:8080/mcp/sse
```

**NOTE**: make sure to point to the correct address of your Wanaku MCP instance.

In LibreChat, you can access Wanaku MCP tools using [Agents](https://www.librechat.ai/docs/features/agents).

### Using an STDIO gateway

Wanaku does not support stdio.
Therefore, to use Wanaku with to use it with tools that don't support SSE, it is
necessary to use an stdio-to-SSE gateway.
The application [super gateway](https://github.com/supercorp-ai/supergateway) can be used for this.

```
npx -y supergateway --sse http://localhost:8080/mcp/sse
```

## Available Resources Providers 

The following resources can be made available using Wanaku.

| Type   | Resource Provider    | Description                                                    |
|--------|----------------------|----------------------------------------------------------------|
| `file` | wanaku-provider-file | Provides access to files as resources to Wanaku                |
| `ftp`  | wanaku-provider-ftp  | Provides access to files in FTP servers as resources to Wanaku |
| `s3`   | wanaku-provider-s3   | Provides access to files in AWS S3 as resources to Wanaku      |

## Available Tools Services

The following tools services can be made available using Wanaku and used to provide access to specific services.

| Type         | Service Tool                      | Description                                                                 |
|--------------|-----------------------------------|-----------------------------------------------------------------------------|
| `http`       | wanaku-routing-http-service       | Provides access to HTTP endpoints as tools via Wanaku                       |
| `yaml-route` | wanaku-routing-yaml-route-service | Provides access to Camel routes in YAML tools via Wanaku                    |
| `kafka`      | wanaku-routing-kafka-service      | Provides access to Kafka topics as tools via Wanaku                         |
| `tavily`     | wanaku-routing-tavily-service     | Provides search capabilities on the Web using [Tavily](https://tavily.com/) |


## Adding Your Own Resource Provider or Tool Service

Wanaku leverages the Apache Camel to provide connectivity to a vast range of services and platforms. Although we 
aim to provide many of them out-of-the box, not all of them will fit all the use cases. That's why we make it 
simple for users to create custom services that solve their particular need.

### Creating a New Resource Provider

To create a custom resource provider, you can run: 

```shell
mvn -B archetype:generate -DarchetypeGroupId=ai.wanaku -DarchetypeArtifactId=wanaku-provider-archetype -DarchetypeVersion=0.0.2 -DgroupId=ai.wanaku -Dpackage=ai.wanaku.provider -DartifactId=wanaku-provider-s3 -Dname=S3 -Dwanaku-version=0.0.2
```

### Creating a New Tool Service

To create a custom tool service, you can run:

```shell
mvn -B archetype:generate -DarchetypeGroupId=ai.wanaku -DarchetypeArtifactId=wanaku-tool-service-archetype -DarchetypeVersion=0.0.2 -DgroupId=ai.wanaku -Dpackage=ai.wanaku.routing.service -DartifactId=wanaku-routing-jms-service -Dname=JMS -Dwanaku-version=0.0.2
```

### Adjusting Your Resource Provider or Tool Service

After created, then most of the work is to adjust the auto-generated `Delegate` class to provide the Camel-based URL and, if 
necessary, coerce the response from an specific type to String. 

In some cases it may also be necessary to implement your own `Client` or `Resource` consumer. 
In those cases, then you also need to write a class that leverages [Apache Camel's](http://camel.apache.org) `ProducerTemplate`
and (or, sometimes, both) `ConsumerTemplate` to interact with the system you are implementing connectivity too. 

### Implementing Services in Other Languages

The communication between Wanaku MCP Router and its downstream services is capable of talking to any type of service using gRPC, 
therefore, it's possible to implement services in any language that supports it. 

For those cases, leverage the `.proto` files in the `core-exchange` module for creating your own service.

**NOTE**: at this time, Wanaku is being intensively developed, therefore, we cannot guarantee backwards compatibility of the protocol. 

**NOTE**: for Java, you can still generate the project using the archetype, but in this case, you must implement your own 
delegate from scratch.
