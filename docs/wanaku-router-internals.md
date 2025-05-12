# Wanaku MCP Router Internals

The root abstraction for all operations within Wanaku MCP Router is the Proxy interface. All MCP
operations within Wanaku are executed by implementations of a Proxy. For details, 
see [`Proxy`](https://github.com/wanaku-ai/wanaku/blob/main/wanaku-router/src/main/java/org/wanaku/routers/camel/proxies/Proxy.java) 
interface. 
Leveraging the `Proxy` interface, then we have the classes `ResourceAcquirerProxy` and `InvokerProxy` that use [gRPC](https://grpc.io/)
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