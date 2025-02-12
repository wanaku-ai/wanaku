# Wanaku MCP Router Internals

The root abstraction for all operations within Wanaku MCP Router is the Proxy interface. All MCP
operations within Wanaku are executed by implementations of a Proxy. For details, 
see [`Proxy`](https://github.com/megacamelus/wanaku/blob/main/routers/wanaku-router/src/main/java/org/wanaku/routers/camel/proxies/Proxy.java) 
interface.

## Resources 

A resource is, essentially, anything that can be read by using the MCP protocol. For instance: 

* Files
* Read-only JMS Queues 
* Topics
* Static resources (i.e.: a web page)

Among other things, resources can be subscribed to, so that changes to its data and state are notified
to the subscribers.

For instance, Wanaku's ability to read files is handled by the `FileProxy` which is an implementation of the `ResourceProxy` interface, which
extends the `Proxy` interface:

```
+---------------+
|   Proxy       |
+---------------+
       |
       |
       v
+---------------+
| ResourceProxy |
+---------------+
       |
       |
       v
+---------------+
|   FileProxy   |
+---------------+
```

Check the [`ResourceProxy`](https://github.com/megacamelus/wanaku/blob/main/routers/wanaku-router/src/main/java/org/wanaku/routers/camel/proxies/ResourceProxy.java) interface for details.

The [`FileProxy`](https://github.com/megacamelus/wanaku/blob/main/routers/wanaku-router/src/main/java/org/wanaku/routers/camel/proxies/resources/FileProxy.java) 
can be used as an example implementation of a resource proxy.

Ideally, resource proxies should leverage Apache Camel's [ConsumerTemplate](https://camel.apache.org/manual/consumertemplate.html). 

## Tools

A tool is anything that can be invoked by an LLM in a request/response fashion and used to provide data to it. 

Examples: 

* Request/reply over JMS
* Calling REST APIs 
* Executing subprocesses that provide an output
* Executing an RPC invocation and waiting for its response

In Wanaku, the tools are concrete proxies that implement the [`ToolsProxy`](https://github.com/megacamelus/wanaku/blob/main/routers/wanaku-router/src/main/java/org/wanaku/routers/camel/proxies/ToolsProxy.java)
interface. 

Here's the hierarchy for the `CamelEndpointProxy`, which is a proxy that invokes endpoints using the Apache Camel [ProducerTemplate](https://camel.apache.org/manual/producertemplate.html).

```
+-------------------+
|      Proxy        |
+-------------------+
         |
         |
         v
+-------------------+
|     ToolsProxy    |
+-------------------+
         |
         |
         v
+-------------------+
|CamelEndpointProxy |
+-------------------+
```

## Resolving Resources and Tools

Resources and Tools are registered during initialization: 

* [`ResourceProvider`](https://github.com/megacamelus/wanaku/blob/main/routers/wanaku-router/src/main/java/org/wanaku/routers/camel/ResourcesProvider.java) register resource proxies.
* [`ToolsProvider`](https://github.com/megacamelus/wanaku/blob/main/routers/wanaku-router/src/main/java/org/wanaku/routers/camel/ToolsProvider.java) register tools proxies.

