# Using the Wanaku MCP Router

Wanaku aims to provide unified access, routing and resource management capabilities for your organization and your AI Agents.

## Meet Wanaku 

If you haven't seen it already, we recommend watching the Getting Started with Wanaku video that introduces the project, 
and introduces how it works.

[![Getting Started With Wanaku](https://img.youtube.com/vi/-fuNAo2j4SA/0.jpg)](https://www.youtube.com/watch?v=-fuNAo2j4SA)

**NOTE**: Also check the Getting Started from the [demos repository](https://github.com/wanaku-ai/wanaku-demos/tree/main/01-getting-started).

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


## Quick Getting Started (Local)

After downloading the CLI, simply run `wanaku start local` and the CLI should download, deploy and start Wanaku with the main 
server, a file provider and an HTTP provider.
If that is successful, open your browser at http://localhost:8080, and you should have access to the UI.

## Quick Getting Started (Docker Compose)

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

Alternatively, using podman:

```shell
podman compose up -d
```

Open Wanaku Console to easily import toolsets, add new tools, resources, and test tools using simple LLMChat:

```shell
open http://localhost:8080
```

![Wanaku Console](https://github.com/user-attachments/assets/4da352f8-719c-4ffb-b3d5-d831a295672f)


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
meow-facts      => http            => https://meowfacts.herokuapp.com?count={parameter.valueOrElse('count', 1)}
dog-facts       => http            => https://dogapi.dog/api/v2/facts?limit={parameter.valueOrElse('count', 1)}
```

### Add Tool

Adds an existing tool to the Wanaku MCP Router instance.

#### Example

Here's how you could add a new tool to a Wanaku MCP router instance running locally on http://localhost:8080:

```shell
wanaku tools add -n "meow-facts" --description "Retrieve random facts about cats" --uri "https://meowfacts.herokuapp.com?count={parameter.valueOrElse('count', 1)}" --type http --property "count:int,The count of facts to retrieve" --required count
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
wanaku toolset add ./path/to/toolset-file.json -n "meow-facts" --description "Retrieve random facts about cats" --uri "https://meowfacts.herokuapp.com?count={parameter.valueOrElse('count', 1)}" --type http --property "count:int,The count of facts to retrieve" --required count
```


### API Note

All CLI commands use the Wanaku management API under the hood. If you need more advanced functionality or want to automate tasks, you may be able to use this API directly.

By using these CLI commands, you can manage resources and tools for your Wanaku MCP Router instance.

## Tools

### Creating URIs

Building the URIs is not always as simple as defining their address. Sometimes, optional parameters need to be filtered out or
query parameters need to be built. To help with that, Wanaku comes with a couple of expressions to build them.

To access the values, ou can use the expression `{parameter.value('name')}`. For instance, to get the value of the parameter `id` 
you would use the expression `{parameter.value('id')}`. You can also provide default values, if none are provided, such as 
`http://my-host/{parameter.valueOrElse('id', 1)}/data` (this would provide the value 1 if the parameter `id` is not set).

It is also possible to build the query part of URIs with the `query` method. For instance, to create an URI such as `http://my-host/data?id=456`
you could use `http://my-host/data{parameter.query('id')}`. If the `id` parameter is not provided, this would generate an URI such as
`http://my-host/data`. This can take multiple parameters, so it is possible to pass extra variables such as 
`{parameter.query('id', 'name', 'location', ...)}`. 

**NOTE**: it is important not to provide the `?` character, as it would be added automatically the parsing code. 

Building the query part of URIs can be quite complex if there are too many. To avoid that, you can use `{parameter.query}` to build 
a query composed of all query parameters.

The values for the queries will be automatically encoded, so a URI defined as `http://my-host/{parameter.query('id', 'name')}` 
would generate `http://my-host/?id=456&name=My+Name+With+Spaces` if provided with a name value of "My Name With Spaces".

### Running Camel Routes as Tools

You can design the routes visually, using [Kaoto](https://kaoto.io/). You need to make sure that the start endpoint for the 
route is `direct:start`. If in doubt, check the [hello-quote.camel.yaml](../samples/routes/camel-route/hello-quote.camel.yaml)
file in the `samples` directory.

To add that route as a tool, you can run something similar to this: 

```shell
wanaku tools add -n "camel-rider-quote-generator" --description "Generate a random quote from a Camel rider" --uri "file:///$(HOME)/code/java/wanaku/samples/routes/camel-route/hello-quote.camel.yaml" --type camel-route --property "wanaku_body:string,The data to be passed to the route"
```

## Special/Reserved Arguments

In the command above, we used the property `wanaku_body`. That is a special property that indicate that the property/argument 
should be part of the body of the data exchange and not as part of a parameter. For instance, consider an HTTP call. In such cases, 
this indicates that the property should be part of the HTTP body, not as part of the HTTP URI. How parameters like these are handled may vary according to the service being used. 

Currently special arguments: 

* `wanaku_body`: special property to indicate that 


## Supported/Tested Client 

Wanaku implements the MCP protocol and, by definition, should support any client that is compliant to the protocol. 

The details below describe how Wanaku MCP router can be used with some prominent MCP clients: 

### Embedded LLMChat for testing

Wanaku Console includes simple LLMChat specificly designed for quick testing of the tools.

```shell
open http://localhost:8080
```

![Embedded LLMChat for testing](https://github.com/user-attachments/assets/7a80aacd-0da8-435b-8cd9-75cc073dfc79)

1. Setup LLM - baseurl, api key, model, and extra parameters
2. Select tools
3. Enter prompt and send

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
| `exec`       | wanaku-routing-exec-service       | Executes a process as a tool (use carefully - there's no input validation)  |

NOTE: some services (i.e.; Tavily, S3, etc.) may require API keys and/or other forms of authentication. Check the README.md files in each service documentation for more details.

## Adding Your Own Resource Provider or Tool Service

Wanaku leverages the Apache Camel to provide connectivity to a vast range of services and platforms. Although we 
aim to provide many of them out-of-the box, not all of them will fit all the use cases. That's why we make it 
simple for users to create custom services that solve their particular need.

### Creating a New Resource Provider

To create a custom resource provider, you can run:

```shell
wanaku services create resource --name y4
```

To run the newly created service enter the directory that was created (i.e.,; `cd wanaku-provider-y4`),
then build the project using Maven (`mvn clean package`).

Then, launch it using:

```shell
java -Dvalkey.host=localhost -Dvalkey.port=6379 -Dvalkey.timeout=10 -Dquarkus.grpc.server.port=9901 -jar target/quarkus-app/quarkus-run.jar
```

You can check if the service was registered correctly using `wanaku targets resources list`.

**NOTE**: remember to set the parameters in the `application.properties` file.

### Creating a New Tool Service

To create a custom tool service, you can run:

```shell
wanaku services create tool --name jms
```

To run the newly created service enter the directory that was created (i.e.,; `cd wanaku-routing-jms-service`), then build the project using Maven (`mvn clean package`). 

Then, launch it using:

```shell
java -Dvalkey.host=localhost -Dvalkey.port=6379 -Dvalkey.timeout=10 -Dquarkus.grpc.server.port=9900 -jar target/quarkus-app/quarkus-run.jar
```

You can check if the service was registered correctly using `wanaku targets tools list`.

**NOTE**: remember to set the parameters in the `application.properties` file. 

To customize your service, adjust the delegate and client classes.

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
