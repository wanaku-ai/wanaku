# Wanaku MCP Router Internals

The root abstraction for all operations within Wanaku MCP Router is the Proxy interface. All MCP
operations within Wanaku are executed by implementations of a Proxy. For details, 
see [`Proxy`](https://github.com/wanaku-ai/wanaku/blob/main/wanaku-router-backend/src/main/java/ai/wanaku/backend/proxies/Proxy.java) 
interface. 

The proxy architecture is organized into specialized interfaces:

* [`ResourceProxy`](https://github.com/wanaku-ai/wanaku/blob/main/wanaku-router-backend/src/main/java/ai/wanaku/backend/proxies/ResourceProxy.java) - Extended interface for resource-specific operations with provisioning and evaluation capabilities
* [`ToolsProxy`](https://github.com/wanaku-ai/wanaku/blob/main/wanaku-router-backend/src/main/java/ai/wanaku/backend/proxies/ToolsProxy.java) - Extended interface for tool-specific operations with provisioning capabilities

Leveraging these specialized interfaces, we have the classes `ResourceAcquirerProxy` and `InvokerProxy` that use [gRPC](https://grpc.io/)
to exchange data with the subcomponents providing access to the resources and tools exposed by the router.
 
## Resources 

A resource is, essentially, anything that can be read by using the MCP protocol. For instance: 

* Files
* Read-only JMS Queues 
* Topics
* Static resources (i.e.: a web page)

Among other things, resources can be subscribed to, so that changes to its data and state are notified
to the subscribers.

For instance, the ability to read files is handled by the `wanaku-provider-file` which is a gPRC server that is capable of
consuming files isolated from other providers:

```
+-----------------------+
|         Proxy         |
+-----------------------+
            |
            |
            v
+-----------------------+
|    ResourceProxy      |
+-----------------------+
            |
            |
            v
+-----------------------+
| ResourceAcquirerProxy |
+-----------------------+
            |
          (gRPC)
            |
            v
+-----------------------+
| Wanaku Provider - File|
+-----------------------+
```

Ideally, providers should leverage [Apache Camel](https://camel.apache.org/) whenever possible. 

## Tools

A tool is anything that can be invoked by an LLM in a request/response fashion and used to provide data to it. 

Examples: 

* Request/reply over JMS
* Calling REST APIs 
* Executing subprocesses that provide an output
* Executing an RPC invocation and waiting for its response

In Wanaku, the every tool invocation is remote and handled by the `InvokerProxy` class which, then, uses the gRPC protocol to 
talk to the service that provides the tool.


```
+-----------------------+
|         Proxy         |
+-----------------------+
            |
            |
            v
+-----------------------+
|      ToolsProxy       |
+-----------------------+
            |
            |
            v
+-----------------------+
|     InvokerProxy      |
+-----------------------+
            |
          (gRPC)
            |
            v
+-----------------------+
| (Any tool provider)   |
+-----------------------+
```

## Configuration and Secrets Provisioning System

Both resource providers and tool services support a provisioning system that handles configuration and secret management. This system allows runtime configuration of services through gRPC-based provisioning requests that establish:

* Configuration URIs for service-specific settings
* Secret URIs for sensitive credentials
* Property schemas defining the expected configuration structure

The provisioning system enables dynamic service configuration without requiring service restarts, supporting flexible deployment scenarios and secure credential management.