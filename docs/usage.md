# Introduction

Wanaku aims to provide unified access, routing and resource management capabilities for your organization and your AI Agents.

## Understanding What Is Wanaku

The Wanaku MCP Router is an integration service designed to securely connect AI agents with various enterprise systems and cloud services.
It acts as a central hub that manages and governs how agents access specific resources and tools, effectively proxying and
filtering capabilities exposed to Large Language Models (LLMs).

The Wanaku MCP Router itself does not directly host tools or resources; instead, it acts as an integration service that connects AI agents with external resources and tools, including enterprise systems and cloud services. It manages and governs access between agent types and specific resources, proxying and filtering available capabilities to agents and their LLM

![HyperChat Configuration](imgs/wanaku-architecture.jpg)

Wanaku provides specialized services, referred to as "capabilities" that offer specific functionalities to the Wanaku MCP Router.

These capabilities enable communication with various systems, such as Kafka services, message brokers, cloud services (AWS, Azure, Google, etc.),
databases and a wide range of enterprise systems, including Workday and Salesforce, without directly containing the tools or resources.

Furthermore, Wanaku features an MCP-to-MCP bridge, which allows it to act as a centralized gateway or proxy for other MCP servers
that use HTTP as the transport mechanism. This capability enables Wanaku to aggregate and effectively "hide" multiple external MCP
servers, simplifying management and increasing the overall functionality of a Wanaku instance. Wanaku is an open-source project and is licensed under Apache 2.0.

### Meet Wanaku 

If you haven't seen it already, we recommend watching the Getting Started with Wanaku video that introduces the project, 
and introduces how it works.

[![Getting Started With Wanaku](https://img.youtube.com/vi/-fuNAo2j4SA/0.jpg)](https://www.youtube.com/watch?v=-fuNAo2j4SA)

> [!NOTE]
> Also check the Getting Started from the [demos repository](https://github.com/wanaku-ai/wanaku-demos/tree/main/01-getting-started).

## Using Wanaku

Using Wanaku MCP Router involves three key actions:

1. Adding tools or resources to the MCP router
2. Forwarding other MCP servers via the MCP forwarder 
3. Adding new capabilities via downstream services

### Adding tools or resources that use those capabilities

Adding tools and resources to the Wanaku MCP Router expands the functionality available to agents using Wanaku. 

* MCP tools equip an agent with capabilities not inherently present in its native models. 
* MCP resources, on the other hand, allow an AI agent to consume data—such as files or records—and inject additional information into its context.

Both tools and resources depend on capabilities that can be dynamically added to or removed from the Wanaku MCP Router. 
Once these capabilities are integrated, either through downstream services or by connecting to other MCP servers, users can then 
incorporate new tools and resources into Wanaku. 
These additions can then leverage the newly integrated capabilities to interact with enterprise systems and cloud services.

### Forwarding other MCP servers via the MCP forwarder

Wanaku can act as a central gateway or proxy to other MCP servers that use HTTP as the transport mechanism.
This feature allows for a centralized endpoint to aggregate tools and resources provided by other MCP servers, making them 
accessible as if they were local to the Wanaku instance.

### Adding new capabilities via downstream services

This refers to extending the router's functionality by integrating with various external systems.

Wanaku leverages Quarkus and Apache Camel to provide connectivity to a vast range of services and platforms.
This allows users to create custom services to solve particular needs.
These services can be implemented in any language that supports gRPC for communication with the Wanaku MCP Router.

> [!NOTE]
> It is also possible to create and run services in Java and other languages, such as Go or Python, although the process is not
> entirely documented at the moment.

# Preparing the System for Running Wanaku 

Security in Wanaku involves controlling access to the management APIs and web interface while ensuring that only authorized
users can modify tools, resources, and configurations. Wanaku also ensures secure access to the MCP tools and resources. 

Wanaku uses [Keycloak](https://keycloak.org) for authentication and authorization. As such, a Keycloak instance needs to be up
and running for Wanaku to work. This section covers the basics of getting Keycloak ready for Wanaku for development and production 
purposes.

## Keycloak Setup for Wanaku

Choose the setup that matches your environment.

* **Local Development:** Use Podman for a quick, local instance.
* **OpenShift Deployment:** Follow these steps for a cluster environment.

### Option 1: Local Setup with Podman

This method is ideal for development and testing on your local machine.

#### Starting the Keycloak Container

First, run the following command in your terminal to start a Keycloak container.
This command also sets the initial admin credentials and maps a local volume for data persistence.

```shell
podman run -d \
  -p 127.0.0.1:8543:8080 \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
  -v keycloak-dev:/opt/keycloak/data \
  quay.io/keycloak/keycloak:26.3.1 start-dev
```

* `-p 127.0.0.1:8543:8080`: Maps port `8543` on your local machine to the container's port `8080`. By default, Wanaku expects Keycloak on port `8543`.
* `-e ...`: Sets the default **admin** username and password. Change the password for any non-trivial use case.
* `-v keycloak-dev...`: Creates a persistent volume named `keycloak-dev` to store Keycloak data.

### Option 2: Deploying to OpenShift or Kubernetes

If you are deploying Wanaku in OpenShift or Kubernetes, you can follow these steps to get an entirely new Keycloak setup up and
running.
If you already have a Keycloak instance, you may skip the deployment section and jump to importing the realm.

#### Deploying Keycloak

Apply the pre-defined Kubernetes configurations located in the [`deploy/auth`](https://github.com/wanaku-ai/wanaku/tree/main/deploy/auth) directory.
This will create all the necessary resources for Keycloak to run.

> [IMPORTANT]
> Before applying, review the files and be sure to change the default admin password for security.

```shell
oc apply -f deploy/auth
```

### Importing the Wanaku Realm Configuration (via CLI)

Next, you'll use Keycloak's Admin API to automatically configure the `wanaku` realm.
Wanaku comes with a [script that simplifies importing](https://github.com/wanaku-ai/wanaku/blob/main/deploy/auth/configure-auth.sh)
the realm configuration into keycloak. 

To run that script: 
- set the `WANAKU_KEYCLOAK_PASS` variable to the admin password of your Keycloak instance
- set `WANAKU_KEYCLOAK_HOST` to the address of your Keycloak instance (i.e.; `localhost` if using Podman or the result of `oc get routes keycloak -o json  | jq -r .spec.host` if using OpenShift)


### Importing the Wanaku Realm Configuration (via Keycloak UI)

Alternatively, you may also import the configuration using Keycloak's UI, and then proceed to regenerate the capabilities' client secret. 

#### Regenerating the Capabilities' Client Secret

Finally, for security, you must regenerate the client secret for the `wanaku-service` client.

1.  Navigate to the Keycloak Admin Console at `http://localhost:8543`.
2.  Log in with your admin credentials (**admin**/**admin**).
3.  Select the **wanaku** realm from the dropdown in the top-left corner.
4.  Go to **Clients** in the side menu and click on **wanaku-service**.
5.  Go to the **Credentials** tab.
6.  Click the **Regenerate secret** button and confirm. Copy the new secret to use in your application's configuration.

![KeyCloak Service](imgs/keycloak-service.png)

# Installing Wanaku

To run Wanaku, you need to first download and install the router and the command line client.

## Installing the CLI

Although the router comes with a UI, the CLI is the primary method used to manage the router. As such, it's recommended to have 
it installed.

#### Installing the CLI by downloading binary

The most recommended method for installing the Wanaku CLI is to download the latest version directly from the
[release](https://github.com/wanaku-ai/wanaku/releases) page on GitHub

#### Installing the CLI via JBang

To simplify using the Wanaku Command Line Interface (CLI), you can install it via [JBang](https://www.jbang.dev/).

First, ensure JBang is installed on your system. You can find detailed [download and installation](https://www.jbang.dev/download/) instructions on the official JBang website.

After installing JBang, verify it's working correctly by opening your command shell and running:

```shell
jbang version
```

This command should display the installed version of JBang.

Next, to access the Wanaku CLI, install it using JBang with the following command:

```shell
jbang app install wanaku@wanaku-ai/wanaku
```
This will install Wanaku CLI as the `wanaku` command within JBang, meaning that you can run Wanaku from the command line by just
executing `wanaku`.

> [!NOTE]
> It requires access to the internet, in case of using a proxy, please ensure that the proxy is configured for your system.
> If Wanaku JBang is not working with your current configuration, please look to [Proxy configuration in JBang documentation](https://www.jbang.dev/documentation/jbang/latest/configuration.html#proxy-configuration).



## Installing and Running the Router

There are three ways to run the router. They work similarly, with the distinction that some of them may come with more 
capabilities by default — continue reading the documentation below for details.

> [IMPORTANT]
> Before the router can be executed, it still needs to be configured for secure access and control of its resources. Make sure 
> you read the section [Securing the Wanaku MCP Router](# Securing the Wanaku MCP Router) **before** running or deploying the router. 

### Installing and Running Wanaku Locally Using "Wanaku Start Local"

You can use the Wanaku CLI to start a small/simplified local instance. To do so, you need to run and configure a local Keycloak 
instance and then use the `wanaku start local` command to run Wanaku pointing to that instance. Make sure you follow the steps
described in [Option 1: Local Setup with Podman](## Option 1: Local Setup with Podman) the [Keycloak Setup For Wanaku](#Keycloak Setup for Wanaku).

After downloading the CLI, simply run `wanaku start local` and the CLI should download, deploy and start Wanaku with the main
server, a file provider and an HTTP provider. You will need to pass the client secret configured 
so that the capabilities can connect to the router. 

```wanaku start local start local --capabilities-client-secret=aBqsU3EzUPCHumf9sTK5sanxXkB0yFtv```

If that is successful, open your browser at http://localhost:8080, and you should have access to the UI.

> [!NOTE]
> You can use the command line to enable more services by using the `--services` option. Use the `--help` to see the details. 

### Installing and Running Wanaku on OpenShift or Kubernetes

It is possible to run Wanaku on Kubernetes distributions, such as OpenShift. The deployment is configured using Kustomize for environment-specific customization.

#### Prerequisites

Before deploying Wanaku on OpenShift, ensure you have:
- Access to an OpenShift or Kubernetes cluster
- `oc` or `kubectl` CLI tools configured
- Sufficient permissions to create deployments, services, and routes

#### Initial Setup Steps

#### Deployment

You can deploy Wanaku in OpenShift or Kubernetes using Kustomize. 

After having deployed Keycloak, then run the following command to get its route:
 
```shell
oc get route keycloak -o jsonpath='{.spec.host}'
```

Lastly, copy the regenerated client secret and add it to the respective overlay:

**For Development Environment:**
```shell
oc apply -k deploy/openshift/kustomize/overlays/dev/
```

**For Production Environment:**
```shell
oc apply -k deploy/openshift/kustomize/overlays/prod/
```

This updates the OIDC server URLs in the environment variable patch files to point to your Keycloak instance.


#### Environment Configuration

When running Wanaku on OpenShift or Kubernetes, capabilities cannot automatically discover the router address. You must configure the router location using environment variables in your deployment:

- Set `WANAKU_SERVICE_REGISTRATION_URI` to point to the actual location of the router
- Configure OIDC authentication URLs to point to your Keycloak instance

The Kustomize overlays handle these configurations automatically for different environments.

> [IMPORTANT]
> This configuration is also required when running the router and the services on different hosts.

### Installing the Command Line Interface (CLI)

In addition to installing the Wanaku MCP Router, it is also necessary to install the CLI used to manage the router.
The Wanaku MCP Router CLI provides a simple way to manage resources and tools for your Wanaku MCP Router instance.

> [!NOTE]
> Wanaku also comes with a web user interface that you can access on port 8080 of the host running the router, but at this
> moment, some features are only available on the CLI.

The MCP endpoint exposed by Wanaku can be accessed on the path `/mcp/sse` of the host you are using (for instance, if running
locally, that would mean `http://localhost:8080/mcp/sse`)


# Securing the Wanaku MCP Router

Security in Wanaku involves controlling access to the management APIs and web interface while ensuring that only authorized
users can modify tools, resources, and configurations.

This section covers how to configure Wanaku for secure access.

> [!NOTE]
> Authentication and authorization currently apply only to the management APIs and UI, not to the MCP endpoints themselves.
> This feature is experimental and under active development.

## Understanding Wanaku Security Model

Wanaku's security model focuses on:

- **API Protection**: Securing management operations for tools, resources, and configuration
- **UI Access Control**: Restricting access to the web console
- **Service Authentication**: Ensuring capability services can authenticate with the router
- **MCP Authentication**: Ensuring MCP calls are authenticated

### MCP Authentication

Currently, Wanaku supports:

* OAuth authentication with code grant
* Automatic client registration

> [IMPORTANT]
> When using the Automatic client registration, the access is granted per-namespace. As such, applications need to request a new
> client id and grant if they change the namespace in use.

For these to work, Keycloak needs to be configured so that the authentication is properly supported.

Wanaku comes with a [template configuration](https://github.com/wanaku-ai/wanaku/blob/main/deploy/auth/wanaku-config.json) that
can be imported into Keycloak to set up the realm, clients and everything else needed for Wanaku to work.

> [IMPORTANT]
> After importing this, make sure to adjust the secrets used by the services and any other potential sensitive configuration.


## Configuring Wanaku Components

Each Wanaku component requires a specific set of configurations to enable authentication.

The configuration varies depending on the component's role in the system.

### Wanaku Router Backend

The backend service handles API operations and requires [OIDC configuration](https://quarkus.io/guides/security-oidc-configuration-properties-reference)
with service credentials.
Some of the configurations you may need to change are:

```properties
# Address of the Keycloak authentication server - adjust to your Keycloak instance
auth.server=http://localhost:8543
# Address used by the OIDC proxy - 
auth.proxy=http://localhost:${quarkus.http.port}

# Client identifier configured in Keycloak for the backend service
quarkus.oidc.client-id=wanaku-mcp-router

# Avoid forcing HTTPS
quarkus.oidc.resource-metadata.force-https-scheme=false
```

#### References

As a reference for understanding what is going on under the hood, the following guides may be helpful:

* [Secure MPC OIDC Proxy](https://quarkus.io/blog/secure-mcp-oidc-proxy/)
* [Secure MCP Server OAuth 2](https://quarkus.io/blog/secure-mcp-server-oauth2/)
* [Secure MCP SSE Server](https://quarkus.io/blog/secure-mcp-sse-server/)

### Capability Services

Wanaku also requires for the capabilities services to be authenticated in order to register themselves.
Capability services act as [OIDC clients](https://quarkus.io/guides/security-openid-connect-client-reference) and authenticate
with the router using client credentials.
Some of the settings you may need to adjust are:

```properties
# Address of the Keycloak authentication server - adjust to your Keycloak instance
auth.server=http://localhost:8543

# Address of the KeyCloak authentication server
quarkus.oidc-client.auth-server-url=${auth.server}/realms/wanaku

# Client secret from Keycloak for service authentication - replace with your actual secret
quarkus.oidc-client.credentials.secret=aBqsU3EzUPCHumf9sTK5sanxXkB0yFtv
```

> [!IMPORTANT]
> - Capability services use the OIDC *client* component (`quarkus.oidc-client.*`), which differs from the main router configuration
> - The client secret values shown here are examples from the default configuration - replace them with your actual Keycloak client secrets
> - Ensure the auth-server-url points to your actual Keycloak instance

## Troubleshooting Security Configuration

Common issues when setting up authentication:

- **Services fail to register**: Ensure capability services have valid OIDC client credentials
- **UI access denied**: Verify user roles and permissions in Keycloak
- **API authentication errors**: Check OIDC configuration and network connectivity

> [!CAUTION]
> This security implementation is experimental. For production deployments, thoroughly test the configuration and consider additional security measures such as network-level access controls.


# Using the Wanaku MCP Router

## Understanding Capabilities

Wanaku itself does not have any builtin MCP tool, resource or functionality itself. The router itself is just a blank MCP server. 

To actually perform its work, Wanaku relies on specialized services that offer the connectivity bridge that enables Wanaku 
to talk to any kind of service. At its core, Wanaku is powered by [Quarkus](https://quarkus.io/) and [Apache Camel](https://camel.apache.org), which provide the ability to connect
to more than [300 different types of systems and services](https://camel.apache.org/components/latest/). 

The power of Wanaku relies on its ability to plug in different types of systems, regardless of them being new 
microservices or legacy enterprise systems. 
For instance, consider the scenario of an enterprise organization, which is running hundreds of systems. With Wanaku, 
it is possible to create a specific capability for each of them (i.e.: a capability for the finance systems, another 
for human resources, another for billing, and so on). 

The granularity on which these capabilities can operate is a decision left to the administrator of the system. For some 
organizations, having a "Kafka" capability to Wanaku capable of talking to any of its systems may be enough. Others, may 
want to have system-specific ones (i.e.: a billing capability, an employee system capability, etc).

The recommended way to create those capabilities is to use the [Camel Integration Capability for Wanaku](https://wanaku.ai/docs/camel-integration-capability/). This is a 
subcomponent of Wanaku that leverages Apache Camel to exchange data with any system that Camel is capable of talking to.

![Wanaku Capabilities](imgs/wanaku-capabilities.jpg)

> [!NOTE]
> Capabilities were, at some point, also called "Downstream services" or "targets". You may still see that terminology 
> used in some places, especially in older documentation.

You should see a list of capabilities available in the UI, in the Capabilities page. Something similar to this:

![Wanaku Capabilities](imgs/capabilities-list.png)

On the CLI, running `wanaku capabilities list` lists the capabilities available for MCP tools:

```shell
service serviceType  host      port status lastSeen
exec    tool-invoker 127.0.0.1 9009 active Sat, Oct 18, 2025 at 18:47:22
http    tool-invoker 127.0.0.1 9000 active Sat, Oct 18, 2025 at 18:47:23
tavily  tool-invoker 127.0.0.1 9006 active Sat, Oct 18, 2025 at 18:47:23
```

Capabilities determine what type of tools you may add to the router. As such, in the output from the CLI above, it means that 
this server can add tools of the following types: `exec`, `tavily`, and `http`. 

Wanaku accepts the following capability service types:

* `tool-invoker`: these capabilities can be used to create MCP tools. 
* `resource-provider`: these capabilities can be used to create MCP resources.
* `multi-capability`: these capabilities can be used to create either MCP tools or MCP resources.

## Managing MCP Tools

An MCP (Model Context Protocol) tool enables Large Language Models (LLMs) to execute tasks beyond their inherent capabilities by
using external functions.
Each tool is uniquely identified by a name and defined with an input schema that outlines the expected parameters. 
Essentially, MCP tools act as a standardized interface through which an AI agent can request information or execute specific 
tasks from external systems, like APIs or databases.

When adding a tool to Wanaku, there are two key considerations:

1. Capability: determine which capability will handle the request and process the input data.
2. Tool/Service Arguments: Identify any arguments (also known as properties) that the tool and/or service accept.

A capability service is required to be available at the moment when a new tool is being added to Wanaku MCP Router.

### Adding Tools Using the CLI

To add a new tool to a Wanaku MCP Router Backend instance running locally on http://localhost:8080, use the following command:

```shell
wanaku tools add -n "meow-facts" --description "Retrieve random facts about cats" --uri "https://meowfacts.herokuapp.com?count={parameter.valueOrElse('count', 1)}" --type http --property "count:int,The count of facts to retrieve" --required count
```

The command `wanaku tools add` is used to register a new tool with the Wanaku MCP Router. Let's break down each part of the command:

* `-n "meow-facts"`: This flag sets the name of the tool to "meow-facts". This is a unique, human-readable identifier for the tool.
* `--description "Retrieve random facts about cats"`: This provides a description of what the tool does, making it clear for users and LLMs.
* `--uri "https://meowfacts.herokuapp.com?count={parameter.valueOrElse('count', 1)}"`: This specifies the URI (Uniform Resource Identifier) that the tool will interact with. In this case, it's an HTTP endpoint that provides cat facts. The {parameter.valueOrElse('count', 1)} part indicates that the count parameter from the tool's input will be used in the URI. If count is not provided, it will default to 1. This demonstrates how Wanaku can dynamically build URIs with parameters.
* `--type http`:  This defines the type of the tool's underlying service, which in this case is `http`. This tells Wanaku that it should use its HTTP service handling capabilities for this tool.
* `--property "count:int,The count of facts to retrieve"`: This defines an input property for the tool named count. It specifies that count is an integer (int) and provides a description of what it represents: `"The count of facts to retrieve"`.
* `--required count`: This flag indicates that the count property is a required input for this tool.

> [!NOTE]
> For remote instances, you can use the parameter `--host` to point to the location of the instance.

> [IMPORTANT]
> The meaning of the `uri` and how to actually compose it, depends on the type of capability being used. Each capability describes
> exactly the meaning of the URI, so make sure to check the capability service for details. Additionally, this is covered in more
> details in the Creating URIs section below.

#### Configuring the Capabilities

Sometimes, specific configurations are required for the downstream services (capabilities) that a tool uses. 
This might include setting timeouts for operations or providing credentials to access a particular resource.

In such scenarios, you can associate configuration and secret files directly with a tool. 
These files will be automatically used by the underlying capabilities each time the tool is invoked.

Here's an example of how to add a tool and link it to configuration and secret files:

```shell
wanaku tools add --host http://localhost:8080 -n "meow-facts" --description "Retrieve random facts about cats" --uri "https://meowfacts.herokuapp.com?count={count or 1}" --type http --property "count:int,The count of facts to retrieve" --required count --configuration-from-file capabilities.properties --secrets-from-file secret.properties
```

In this command:

* `--configuration-from-file capabilities.properties`: This flag specifies a file named `capabilities.properties` that contains configuration settings to be passed to the underlying capability whenever the `"meow-facts"` tool is used. 
* `--secrets-from-file secret.properties`: This flag points to a file named `secret.properties` that holds sensitive information (like API keys or passwords) needed by the capability to access resources, ensuring secure handling of credentials.

Some capabilities within Wanaku are designed to interpret these configuration settings to dynamically adjust how they interact 
with external systems. 
For instance, Camel-based capabilities leverage these settings, particularly those prefixed with `query.`, to modify the Camel 
URI used for the endpoint.

Consider the following example in a configuration file:

```properties
key=value
query.addKey=addedValue
```

In this scenario, a Camel-based capability would automatically append `addKey=addValue` to the URI passed to the underlying 
Camel producer.
This allows for flexible and dynamic adjustment of endpoint parameters based on the provided configuration.

Secrets behave just as similarly, but are adapted by the capabilities for secure handling of the data.

### Adding Tools Using the UI

It is also possible to add new tools using the UI, by accessing the Tools page and filling the form.

![Wanaku Console](https://github.com/user-attachments/assets/4da352f8-719c-4ffb-b3d5-d831a295672f)


### Importing a ToolSet

Wanaku ToolSets are collections of tools that can be easily shared and imported into your Wanaku router. 
This feature allows for convenient distribution of pre-configured tools among users.

Wanaku provides a [selection of ready-to-use ToolSets](https://github.com/wanaku-ai/wanaku-toolsets) that you can import to 
quickly get started and explore its functionalities.


To import a ToolSet directly into your router from a URL, use the following command:

```shell
wanaku tools import https://raw.githubusercontent.com/wanaku-ai/wanaku-toolsets/refs/heads/main/toolsets/currency.json
```

If you have a ToolSet definition file already stored on your local machine, you can import it using its file path:

```shell
wanaku tools import /path/to/the/toolsets/currency.json
```

### Viewing Tools

You can check what tools are available in a Wanaku MCP Router instance by running: 

```shell
wanaku tools list
```

### Editing Tools

The `wanaku tools edit` command enables you to modify the existing definition of a tool that is registered with your Wanaku MCP 
Router. 
This command provides a convenient way to update a tool's JSON definition directly within your terminal using the `nano` text editor.

```shell
wanaku tools edit [options] [toolName]
```

In this command:

* `toolName` : (Optional) Specifies the exact name of the tool you wish to modify. If this argument is omitted,
  the command will present you with an interactive, scrollable list of all currently registered tools,
  allowing for easy selection.


If you know the precise name of the tool you want to edit, you can specify it directly. 

For example, to edit a tool named "my-custom-tool":

```shell
wanaku tools edit my-custom-tool
```

Upon executing this command, Wanaku will fetch the JSON definition of `"my-custom-tool"` and open it in the nano editor within 
your terminal.
After making your desired changes, save them (usually by pressing `Ctrl+S`) and then exit nano (`Ctrl+X`). 
Wanaku will then ask for your confirmation before applying the updates to the tool's definition.

When you're unsure of the exact tool name or want to browse available tools, run the edit command without specifying a toolName:


```shell
wanaku tools edit
```

This will present an interactive, scrollable list of all your registered tools.

Use your keyboard's arrow keys to navigate and highlight the tool you wish to edit, then press Enter.

The selected tool's JSON definition will then open in nano for you to make your modifications.

### Listing Tools

Any available tool is listed by default when you access the UI.

When using the CLI, the `wanaku tools list` command allows you to view all available tools on your Wanaku MCP Router instance. 

Running this command will display a comprehensive list of tools, including their names and descriptions.

```shell
wanaku tools list
```

For example, you should receive an output similar to this.

```shell
Name               Type               URI
meow-facts      => http            => https://meowfacts.herokuapp.com?count={parameter.valueOrElse('count', 1)}
dog-facts       => http            => https://dogapi.dog/api/v2/facts?limit={parameter.valueOrElse('count', 1)}
```

### Removing Tools

Tools can be removed from the UI,
by clicking on the Trash icon, or on the CLI by running the command `wanaku tools remove --name [name]`. 

For instance:

`wanaku tools remove --name "meow-facts"`

### Generating Tools

The `wanaku tools generate` command converts an OpenAPI specification into a collection of tool references
that can be used by an AI agent.

It parses and resolves OpenAPI paths and operations, transforming them into a standardized tool reference
format for HTTP services.

This command accepts an OpenAPI specification file (either as a local path or URL) and produces a JSON output containing
tool references.

Each operation in the API is converted to a tool reference with appropriate metadata, including the operation's name,
description, URI template, and input schema.

The command handles server variable substitution, proper formatting of path parameters according to the tool reference specification.

By default, the command uses the first server defined in the OpenAPI specification, but you can override this behavior by 
specifying a different server URL or selecting a different server from the specification by index.

The generated output can be directed to standard output or saved to a file.

If the process completes successfully, the command returns exit code `0`. It returns exit code `3` if no paths are found in the 
specification and exit code `2` if an error occurs during processing.

> [NOTE]
> The command support both `json` and `yaml` definition:

For example:

```shell
wanaku tools generate http://petstore3.swagger.io/api/v3/openapi.yaml
```

If the spec defines a server url that contains variables

```yaml
servers:
  - url: 'https://{env}.domain.com/foo/{v1}/{v2}/{v3}'
    variables:
      env:
        description: Environment - staging or production
        default: stage-api
        enum:
          - stage-api
          - api
      # other variables
      # ...
```

Then, you can specify values as command parameters:

```shell
wanaku tools generate --server-variable env=prod --server-variable v1=first http://petstore3.swagger.io/api/v3/openapi.json
```

If not specified for a variable in the server URL template, the default value defined in the OpenAPI specification will be used.

It only applies when using servers from the OpenAPI specification (not when using `--server-url`).

Variables must be defined in the server object of the OpenAPI specification.

Empty or null values for either key or value will be ignored.

OpenAPI specifications can define multiple server URLs:

```json
{
  "servers": [
    {
      "url": "https://api.example.com/v1",
      "description": "Production server"
    },
    {
      "url": "https://staging-api.example.com/v1",
      "description": "Staging server"
    },
    {
      "url": "http://localhost:8080/v1",
      "description": "Local development server"
    }
  ]
}
```

The `--server-index` (or `-i`) option allows you to specify which server definition from the OpenAPI specification should be 
used as the base URL for tool references.

```shell
wanaku tools generate -i 1 ./openapi-spec.yaml
```

This option is ignored if `--server-url` is specified, as an explicit URL overrides any server definitions in the
specification.

If neither `--server-index` nor `--server-url` is specified, the command will default to using the first server (index `0`)
from the specification.

The `--server-index` option can be used together with `--server-variable` when the selected server has variable templates:

```yaml
servers:
  - url: https://{environment}.api.example.com/{version}
    variables:
      environment:
        default: dev
      version:
        default: v1
  - url: https://{environment}.api2.example.com/{version}
    variables:
      environment:
        default: dev
      version:
        default: v1
  - url: https://{environment}.api3.example.com/{version}
    variables:
      environment:
        default: dev
      version:
        default: v1            
```

You could select this server and override its variables:

```shell
wanaku tools generate -i 0 -v environment=prod -v version=v2 ./openapi-spec.yaml
```

The `--output-file` (or `-o`) option specifies the file path where the generated tool references should be written.

It determines where the output JSON containing all the tool references will be saved.

```shell
wanaku tools generate -o ./toolsets/api-tools.json http://petstore3.swagger.io/api/v3/openapi.json
```

If `--output-file` is specified, the command will write the JSON toolset to the specified file path.

If `--output-file` is not specified, the command will write the JSON toolset to standard output (STDOUT).

If the specified path is a directory, the command will write to a file named `out.json` within that directory and provide
a warning message.

If the specified file already exists, the command will return an error without overwriting the file.
The parent directory of the specified file must exist and be writable by the current user.

If the `--import` (or `-I`) option is set, the generated toolset is automatically imported into the router, equivalent
to running the generate command followed by the import command.

## Managing MCP Resources


### Exposing Resource

The wanaku resources expose command allows you to make an existing resource available via your Wanaku MCP Router instance. 

Just like tools, it also requires a capability that can access the system storing and providing access to the resource (i.e.: FTP, 
AWS S3, NFS, etc.).

For example, suppose you have a file named `test-mcp-2.txt` on your home directory on host that has the `file` capability running, 
and you want to expose it.

This is how you can do it:

```shell
wanaku resources expose --location=$HOME/test-mcp-2.txt --mimeType=text/plain --description="Sample resource added via CLI" --name="test mcp via CLI" --type=file
```

In this example:

* `--location=$HOME/test-mcp-2.txt`: Specifies the local path to the resource you want to expose.
* `--mimeType=text/plain`: Defines the MIME type of the resource, indicating its content format.
* `--description="Sample resource added via CLI"`: Provides a descriptive text for the resource.
* `--name="test mcp via CLI"`: Assigns a human-readable name to the exposed resource.
* `--type=file`: Indicates that the exposed resource is a file.

> [IMPORTANT]
> It's important to note that this location refers to a location that the capability (downstream service) is able to access. 
> The exact meaning of "location" depends on the type of the capability. For example:
> * For a `file` type, it means the capability needs direct access to the file, implying it's likely running on a host with direct physical access to the file.
> * For an `ftp` type, it means the capability needs access to the FTP server storing the file.
> 
> Always check the documentation for the capability provider that you are using for additional details about the location specifier.


### Listing Resources

The wanaku resources list command allows you to view all resources currently exposed by your Wanaku MCP Router instance.

Executing this command will display a list of available resources, including their names and descriptions.

```shell
wanaku resources list
```

## Managing Capabilities

Configurations in Wanaku have two distinct scopes:

1. Capability service configurations
2. Tool definition configurations

### Capability Service Configurations

These configurations are essential for setting up the capability provider itself. 

This includes details required for the transport mechanism used to access the capability, such as usernames and passwords for 
authenticating with the underlying system that provides the capability.

Each capability service may have its own specific set of configurations. As such, check the capability service documentation 
for details.

### Tool Definition Configurations 

These configurations are specific to individual tools that leverage a particular capability. They include:

* Names and identifiers that differentiate tools using the same capability, like specific Kafka topics or the names of database tables.
* Operational properties that dictate how the tool behaves, such as the type of HTTP method (`GET`, `POST`, `PUT`), or operational settings like timeout configurations and idempotence flags.

These configurations are handled when adding a new tool to Wanaku MCP Router.

> [NOTE] 
> Check the "Configuring the Capabilities" section for additional details about this.


### Listing Capabilities

The `wanaku capabilities list` command provides a comprehensive view of all service capabilities available in the Wanaku Router.
It discovers and displays both management tools and resource providers, along with their current operational status and
activity information.

The command combines data from multiple API endpoints to present a unified view of the system's capabilities in an
easy-to-read table format.


The command displays the results in a table with the following columns:

| Column | Description |
|--------|-------------|
| **service** | Name of the service |
| **serviceType** | Type/category of the service |
| **host** | Hostname or IP address where the service runs |
| **port** | Port number the service listens on |
| **status** | Current operational status (`active`, `inactive`, or `-`) |
| **lastSeen** | Formatted timestamp of last activity |

For instance, running the command, should present you with an output similar to this:

#### Sample Output
![img.png](imgs/cli-capabilities-list.png)

### Displaying Service Capability Details

The `wanaku capabilities show` command lets you view detailed information for a specific service capability within the 
Wanaku MCP Router.

This includes its configuration parameters, current status, and connection information.


```bash
wanaku capabilities show <service> [--host <url>]
```

* `<service>`: The service name to show details for (e.g., http, sqs, file)
* `--host <url>`: The API host URL (default: http://localhost:8080)


When you execute the command, Wanaku displays comprehensive details about the chosen service type. 
If multiple instances of the same service exist, an interactive menu will appear, allowing you to select the specific instance 
you wish to view.

For example, to show the details for the HTTP service:

```shell
wanaku capabilities show http
```

Or, show details for SQS service linked with to a specific Wanaku MCP router running at `http://api.example.com:8080`:

```shell
wanaku capabilities show sqs --host http://api.example.com:8080
```

The command displays two main sections:

1. **Capability Summary**: Basic service information in table format:
- Service name and type
- Host and port
- Current status
- Last seen timestamp

2. **Configurations**: Detailed configuration parameters:
- Parameter names
- Parameter descriptions

![img.png](imgs/capabilities-show.png)


#### Interactive Selection

When multiple instances of the same service are found, you'll see:
- A warning message indicating multiple matches
- An interactive selection prompt with service details
- Choose your desired instance using arrow keys and Enter

![img.png](imgs/capabilities-show-choose.png)

> [NOTE]
> The Wanaku CLI provides clear exit codes to indicate the outcome of a command:
> - `0`: The command executed successfully.
> - `1`: An error occurred (e.g., no capabilities were found, or there were issues connecting to the API).

### Listing Targets

You can view linked targets by using either the `wanaku targets tools list` command (to see targets for tools) or the
`wanaku target resources list` command (to see targets for resources).

For instance, running listing the targets for tools, you should expect a response similar to this:

```shell
ID                                       SERVICE                 TARGET                        
bee5f297-4f7a-4d1c-b0c6-0ac372fcae2c  => exec                 => 192.168.1.137                         
eaf7a675-2225-40da-965b-d576c1439b92  => kafka                => 192.168.1.137                 
92380df9-bcd3-43cb-a4c2-eabe7b06b415  => tavily               => 192.168.1.137                 
2b70d26b-6d87-4931-8415-684c0d8ca45e  => camel-yaml           => 192.168.1.137                  
```

> [NOTE]
> The difference between `wanaku targets (tools|resources) list` and the `wanaku capabilities list` is that the 
> listing targets print the ID, which can be helpful when extending Wanaku. For most cases, users should rely on the 
> `wanaku capabilities list` feature. 

## Accessing Other MCP servers (MCP Forwards)

The MCP bridge in Wanaku allows it to act as a central gateway or proxy to other MCP servers that use HTTP as the transport mechanism.

This feature enables a centralized endpoint for aggregating tools and resources provided by other MCP servers.

### Listing Forwards

To view a list of currently configured forwards, use the `wanaku forwards list` command:

```bash
wanaku forwards list
```

This command displays information about each forward, including its name, service URL, and any other relevant details.

This can be useful for managing and troubleshooting MCP server integrations.

### Adding Forwards

To add an external MCP server to the Wanaku instance, use the `wanaku forwards add` command:

```bash
wanaku forwards add --service="http://your-mcp-server.com:8080/mcp/sse" --name my-mcp-server
```

* `--service`: The URL of the external MCP server's SSE (Server-Sent Events) endpoint.
* `--name`: A unique human-readable name for the forward, used for identification and management purposes.

Once a forward is added, all tools and resources provided by the external MCP server will be mapped in the Wanaku instance.

These tools and resources can then be accessed as if they were local to the server.

### Removing Forwards

To remove an external MCP server from the Wanaku instance, use the `wanaku forwards remove` command:

```bash
wanaku forwards remove --service="http://your-mcp-server.com:8080/mcp/sse" --name my-mcp-server
```

* `--service`: The URL of the external MCP server's SSE (Server-Sent Events) endpoint.
* `--name`: The human-readable name for the forward to be removed.

Note that attempting to remove a non-existent forward will result in an error message. If you want to remove multiple forwards, simply repeat the command with different names and service URLs.

### Example Use Case

Suppose you have two MCP servers: `http://mcp-server1.com:8080/mcp/sse` and `http://mcp-server2.com:8080/mcp/sse`.

To integrate these external MCP servers into your Wanaku instance, follow these steps:

1.  Add the first forward using the `wanaku forwards add` command:

```shell
wanaku forwards add --service="http://mcp-server1.com:8080/mcp/sse" --name mcp-server-1
```

2.  Use the `wanaku forwards list` command to confirm that the forward has been successfully added:

```bash
wanaku forwards list
``` 

3. Verify that all tools and resources from `mcp-server1` are now accessible within your Wanaku instance using `wanaku tools list`

```shell
Name               Type               URI
tavily-search-local => tavily          => tavily://search?maxResults={parameter.value('maxResults')}
meow-facts      => mcp-remote-tool => <remote>
dog-facts       => mcp-remote-tool => <remote>
camel-rider-quote-generator => mcp-remote-tool => <remote>
tavily-search   => mcp-remote-tool => <remote>
laptop-order    => mcp-remote-tool => <remote>
```

4.  Add the second forward using the same command:
```bash
wanaku forwards add --service="http://mcp-server2.com:8080/mcp/sse" --name mcp-server-2
```

5. Confirm that tools and resources from both external MCP servers are now integrated into your Wanaku instance (use `wanaku tools list`)
6. Use the `wanaku forwards list` command to view the updated list of forwards:
```bash
wanaku forwards list
```

By leveraging the MCP bridge feature, you can create a centralized endpoint for aggregating tools and resources from multiple
external MCP servers, simplifying management and increasing the overall functionality of your Wanaku instance.

## Managing Namespaces

Wanaku introduces the concept of namespaces to help users organize and isolate tools and resources, effectively managing the 
Large Language Model (LLM) context. This prevents context bloat and improves the efficiency of your Wanaku deployments.

### What Are Namespaces

Namespaces provide a mechanism to group related tools and resources. 

Each namespace acts as a separate logical container, ensuring that the LLM context for tools within one namespace does not 
interfere with tools in another.
This is particularly useful when you have a large number of tools or when different sets of tools are used for distinct purposes.

Wanaku provides a fixed set of 10 available slots for namespaces, named from `ns-0` to `ns-9`.

### Using Namespaces

To associate a tool or resource with a specific namespace, use the `--namespace` option when adding it:

```shell
wanaku tools add -n "meow-facts-3" --description "Retrieve random facts about cats" --uri "https://meowfacts.herokuapp.com?count={count or 1}" --type http --property "count:int,The count of facts to retrieve" --namespace test --required count
```

In the example above, the _`meow-facts-3`_ tool will be associated with the first freely available namespace. 

When you provide a namespace name like _`test`_, Wanaku automatically associates it with an available numerical slot from ns-0 
to ns-9.

### Checking Namespace Assignments

You can verify which namespace a tool or resource has been assigned to by using the `wanaku namespaces list` command.

This command will display a list of all active namespaces, their unique IDs, and their corresponding paths.

The output will look similar to this:

```shell
id                                   name path
381d4276-c824-4bbe-9094-a962c6e8fc46 test http://localhost:8080/ns-9/mcp/sse
4b7a5ec7-c1f3-4311-8067-10148daf3a10      http://localhost:8080/ns-3/mcp/sse
dcf97b5e-8ff7-4d04-944c-194379f2e0e4      http://localhost:8080/ns-2/mcp/sse
dd6f75ac-7b32-4f3b-b965-99040f4af6c2      http://localhost:8080/ns-8/mcp/sse
59e92c00-04d5-4673-a631-d9244c3e07c1      http://localhost:8080/ns-0/mcp/sse
e3ff6cd0-e73d-431f-96c9-327b3a498265      http://localhost:8080/ns-1/mcp/sse
ffe8d322-6ebe-46f2-b913-ac792571fadc      http://localhost:8080/ns-7/mcp/sse
82434059-45b4-442c-b945-385ae36f158d      http://localhost:8080/ns-6/mcp/sse
901ea4d6-8e08-4e8b-8171-ce23ae1380d4      http://localhost:8080/ns-5/mcp/sse
4b2403ca-1acd-419f-9e83-102bbf631536      http://localhost:8080/ns-4/mcp/sse
<default>                                 http://localhost:8080//mcp/sse
```

In this output, you can see the mapping of internal namespace IDs to their corresponding ns-X paths.

### The Default Namespace

If you do not specify a namespace when adding a tool or resource, it will automatically be added to the default namespace.

The default namespace acts as a general container for tools that don't require specific isolation.

You can identify the default namespace in the wanaku namespaces list output by its `<default>` name.

## Understanding URIs

Universal Resource Identifiers (URI) are central to Wanaku.

They are used to define the location of resources, the tool invocation request that Wanaku will receive from the Agent/LLM and
the location of configuration and secret properties.

Understanding URIs is critical to leverage Wanaku and create flexible definitions of tools and resources.

### Flexible Input Data

Some services may require a more flexible definition of input data.

For instance, consider HTTP endpoints with dynamic parameters:

* `http://my-host/api/{someId}`
* `http://my-host/api/{someId}/create`
* `http://my-host/api/{someId}/link/to/{anotherId}`

In cases where the service cannot predetermine the actual tool addresses, users must define them when creating the tool.

### Creating URIs

Building the URIs is not always as simple as defining their address. Sometimes, optional parameters need to be filtered out or
query parameters need to be built. To help with that, Wanaku comes with a couple of expressions to build them.

To access the values, ou can use the expression `{parameter.value('name')}`. For instance, to get the value of the parameter `id` 
you would use the expression `{parameter.value('id')}`. You can also provide default values if none are provided, such as 
`http://my-host/{parameter.valueOrElse('id', 1)}/data` (this would provide the value `1` if the parameter `id` is not set).

It is also possible to build the query part of URIs with the `query` method. For instance, to create a URI such as `http://my-host/data?id=456`
you could use `http://my-host/data{parameter.query('id')}`. If the `id` parameter is not provided, this would generate a URI such as
`http://my-host/data`. This can take multiple parameters, so it is possible to pass extra variables such as 
`{parameter.query('id', 'name', 'location', ...)}`. 

> [!IMPORTANT]
> Do not provide the `?` character.
> It is added automatically the parsing code if necessary. 

Building the query part of URIs can be quite complex if there are too many. To avoid that, you can use `{parameter.query}` to build 
a query composed of all query parameters.

The values for the queries will be automatically encoded, so a URI defined as `http://my-host/{parameter.query('id', 'name')}` 
would generate `http://my-host/?id=456&name=My+Name+With+Spaces` if provided with a name value of `"My Name With Spaces"`.

### Dealing with Request Bodies

The `wanaku_body` property is a special argument used to indicate that the associated property or argument should be included in
the body of the data exchange, rather than as a parameter.

For instance, in an HTTP call, `wanaku_body` specifies that the property should be part of the HTTP body, not the HTTP URI.

The handling of such parameters may vary depending on the service being used. 

Currently special arguments: 

* `wanaku_body` 

## Extending Wanaku: Adding Your Own Capabilities

Wanaku leverages [Quarkus](https://quarkus.io/) and [Apache Camel](https://camel.apache.org) to provide connectivity to a vast 
range of services and platforms. 

Although we aim to provide a few of them out-of-the box, not all of them will fit all the use cases. For most cases, users
should rely on the [Camel Integration Capability for Wanaku](https://wanaku.ai/docs/camel-integration-capability/). That capability
service leverages Apache Camel which offers more than 300 components capable of talking to any type of system. Users can design 
their integrations using tools such as [Kaoto](https://kaoto.io/) or Karavan and expose the routes as tools or resources using 
that capability service.

### Adding a New Resource Provider Capability

For cases where the Camel Integration Capability is not sufficient, users can create their own capabibility services. 

Why try to make it simple for users to create custom services that solve their particular need.

#### Creating a New Resource Provider

To create a custom resource provider, you can run:

```shell
wanaku capabilities create resource --name y4
```

To run the newly created service enter the directory that was created (i.e.,; `cd wanaku-provider-y4`),
then build the project using Maven (`mvn clean package`).

> [!NOTE]
> Capabilities services are created, by default, using [Apache Camel](http://camel.apache.org). However, it is possible to create
> purely Quarkus-based capabilities using the option `--type=quarkus`.

Then, launch it using:

```shell
java -Dwanaku.service.registration.uri=http://localhost:8080 -Dquarkus.grpc.server.port=9901 ... -jar target/quarkus-app/quarkus-run.jar
```

You can check if the service was registered correctly using `wanaku targets resources list`.

> [!IMPORTANT]
> Remember to set the parameters in the `application.properties` file and also adjust the authentication settings.

#### Adjusting Your Resource Capability

After created, then most of the work is to adjust the auto-generated `Delegate` class to provide the Camel-based URI and, if
necessary, coerce (convert) the response from its specific type to String.

### Adding a New Tool Invoker Capability

#### Creating a New Tool Service

To create a custom tool service, you can run:

```shell
wanaku capabilities create tool --name jms
```

> [!NOTE]
> Capabilities services are created, by default, using [Apache Camel](http://camel.apache.org). However, it is possible to create
> purely Quarkus-based capabilities using the option `--type=quarkus`.

To run the newly created service enter the directory that was created (i.e.,; `cd wanaku-tool-service-jms`), then build the project using Maven (`mvn clean package`).

Then, launch it using:

```shell
java -Dwanaku.service.registration.uri=http://localhost:8080 -Dquarkus.grpc.server.port=9900 ... -jar target/quarkus-app/quarkus-run.jar
```
You can check if the service was registered correctly using `wanaku targets tools list`.

> [!IMPORTANT]
> Remember to set the parameters in the `application.properties` file and also adjust the authentication settings.

To customize your service, adjust the delegate and client classes.

#### Adjusting Your Tool Invoker Capability

After created, then most of the work is to adjust the auto-generated `Delegate` and `Client` classes to invoke the service and 
provide the returned response.

In those cases, then you also need to write a class that leverages [Apache Camel's](http://camel.apache.org) `ProducerTemplate`
and (or, sometimes, both) `ConsumerTemplate` to interact with the system you are implementing connectivity too.

### Adding a New Mcp server Capability

#### Creating a New Mcp server

To create a custom mcp server, you can run:

```shell
wanaku capabilities create mcp --name s3
```

To run the newly created service enter the directory that was created (i.e.,; `cd wanaku-mcp-servers-s3`),
then build the project using Maven (`mvn clean package`).

> [!NOTE]
> Capabilities services are created, by default, using [Apache Camel](http://camel.apache.org). However, it is possible to create
> purely Quarkus-based capabilities using the option `--type=quarkus`.

Then, launch it using:

```shell
java -Dwanaku.service.registration.uri=http://localhost:8080 -Dquarkus.grpc.server.port=9901 ... -jar target/quarkus-app/quarkus-run.jar
```

You can check if the service was registered correctly using `wanaku targets mcp list`.

> [!IMPORTANT]
> Remember to set the parameters in the `application.properties` file.

#### Adjusting Your Mcp server Capability

After created, then most of the work is to adjust the auto-generated `Tool` class to implement the mcp server tool.

### Implementing Services in Other Languages

The communication between Wanaku MCP Router and its downstream services is capable of talking to any type of service using gRPC.
Therefore, it's possible to implement services in any language that supports it.

For those cases, leverage the `.proto` files in the `core-exchange` module for creating your own service.

> [!CAUTION]
> At this time, Wanaku is being intensively developed, therefore, we cannot guarantee backwards compatibility of the protocol.

> [!NOTE]
> For plain Java, you can still generate the project using the archetype, but in this case, you must implement your own
delegate from scratch and adjust the dependencies.

### Adjusting the announcement address

You can adjust the address used to announce to the MCP Router using either (depending on whether using a tool or a resource provider):

* `wanaku.service.registration.announce-address=my-host`

This is particularly helpful when running a capability service in the cloud, behind a proxy or firewall. 

### Adjusting the authentication parameters

* `quarkus.oidc-client.auth-server-url=http://localhost:8543/realms/wanaku`
* `quarkus.oidc-client.client-id=wanaku-service`
* `quarkus.oidc-client.refresh-token-time-skew=1m`
* `quarkus.oidc-client.credentials.secret=<insert key here>`

## Supported/Tested Clients 

Wanaku implements the MCP protocol and, by definition, should support any client that is compliant to the protocol. 

The details below describe how Wanaku MCP router can be used with some prominent MCP clients: 

### Embedded LLMChat for testing

Wanaku Console includes simple LLMChat specificly designed for quick testing of the tools.

> [!NOTE]
> At the moment, the Embedded LLMChat supports only the tools.

```shell
open http://localhost:8080
```

![Embedded LLMChat for testing](https://github.com/user-attachments/assets/7a80aacd-0da8-435b-8cd9-75cc073dfc79)

1. Setup LLM - `baseurl`, `api key`, `model`, and extra parameters
2. Select tools
3. Enter prompt and send

### Creating New MCP Server Using Maven
 
```shell
mvn -B archetype:generate -DarchetypeGroupId=ai.wanaku -DarchetypeArtifactId=wanaku-mcp-servers-archetype \ 
  -DarchetypeVersion=0.0.8 -DgroupId=ai.wanaku -Dpackage=ai.wanaku.mcp.servers.s3 -DartifactId=wanaku-mcp-servers-s3 \
  -Dname=S3 -Dwanaku-version=0.0.8 -Dwanaku-capability-type=camel
```

> [!IMPORTANT]
> When using the maven way, please make sure to adjust the version of Wanaku
> to be used by correctly setting the `wanaku-version` property to the base Wanaku version to use.

### Adjusting the MCP Server

After creating the mcp server, open the `pom.xml` file to add the dependencies for your project. 
Using the example above, we would include the following dependencies:

```xml
    <dependency>
      <groupId>org.apache.camel.quarkus</groupId>
      <artifactId>camel-quarkus-aws-s3</artifactId>
    </dependency>
```

Adjust the gPRC port in the `application.properties` file by adjusting the `quarkus.grpc.server.port` property.

> [!NOTE]
> You can also provide the port when launching 
> (i.e., `java -Dquarkus.grpc.server.port=9190 -jar target/quarkus-app/quarkus-run.jar`)

Then, build the project:

```shell
mvn clean package
```

And run it: 

```shell
java -jar target/quarkus-app/quarkus-run.jar
```

### Claude

To integrate Wanaku with Claude Desktop, you will need to add an entry into the `claude_desktop_config.json` file - see [instructions for creating a Claude desktop configuration](https://modelcontextprotocol.io/quickstart/user) if you do not already have one.

Claude Desktop does not currently support connecting to SSE-based endpoints, so you will have to configure wanaku using a stdio-to-sse wrapper.   Note that you will have to install ![uv](https://github.com/astral-sh/uv) for this purpose, and specify the SSE URL for your Wanaku instance in the arguments.

```claude_desktop_config.json
{
  "mcpServers": {
    "wanaku": {
        "command": "uvx",
        "args": [
            "mcp-proxy",
            "http://localhost:8080/mcp/sse/"
        ]
      }
  }
}
```


### HyperChat 

Wanaku works with [HyperChat](https://github.com/BigSweetPotatoStudio/HyperChat). To do so,
you can configure Wanaku as an MCP server using the MCP configuration as shown below:

![HyperChat Configuration](imgs/hyperchat-configuration.png)

> [!IMPORTANT]
> Make sure to have Wanaku up and running before configuring HyperChat. You may also need to 
> close and reopen HyperChat.

After configuring HyperChat, you may need to go the Main Window and edit any existing agent if you have any.
Then, in the agent configuration Window, in the `allowMCPs` option, make sure you mark Wanaku as an allowed MCP server. If in 
doubt, check the HyperChat project documentation.

> [!NOTE]
> Older versions of HyperChat (pre 1.1.13) required manually editing the `mcp.json` file as described on the
> [improvement ticket](https://github.com/BigSweetPotatoStudio/HyperChat/issues/30). This is not necessary
> for newer versions.


### LibreChat

For [LibreChat](https://www.librechat.ai/docs) search for `mcpServers` on the `librechat.yml` file and include something similar to this:
   
```
mcpServers:
    everything:
        url: http://host.docker.internal:8080/mcp/sse
```

> [!IMPORTANT]
> Make sure to point to the correct address of your Wanaku MCP instance.

In LibreChat, you can access Wanaku MCP tools using [Agents](https://www.librechat.ai/docs/features/agents).

### Witsy

We also have tested Wanaku with [Witsy - AI Desktop Assistant](https://github.com/nbonamy/witsy/).


### Using an STDIO gateway

Wanaku does not support stdio.
Therefore, to use Wanaku with to use it with tools that don't support SSE, it is
necessary to use an stdio-to-SSE gateway.
The application [super gateway](https://github.com/supercorp-ai/supergateway) can be used for this.

```
npx -y supergateway --sse http://localhost:8080/mcp/sse
```

## Available Resources Capabilities 

Visit [this page](../capabilities/providers/README.md) to check all the providers that come built-in with Wanaku.

> [NOTE]
> Most users should rely on the [Camel Integration Capability for Wanaku](https://wanaku.ai/docs/camel-integration-capability/).

## Available Tools Capabilities

Visit [this page](../capabilities/tools/README.md) to check all the tools that come built-in with Wanaku.

> [NOTE]
> Most users should rely on the [Camel Integration Capability for Wanaku](https://wanaku.ai/docs/camel-integration-capability/).

### API Note

All CLI commands use the Wanaku management API under the hood. If you need more advanced functionality or want to automate tasks, you may be able to use this API directly.

By using these CLI commands, you can manage resources and tools for your Wanaku MCP Router instance.


## Configuring the Wanaku MCP Router

You can find a comprehensive list of configuration options for Wanaku in the [Configuration Guide](configurations.md).