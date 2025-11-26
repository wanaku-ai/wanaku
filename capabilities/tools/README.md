---
layout: home
---

# Available Builtin Tools Services

The following tools services can be made available using Wanaku and used to provide access to specific services.

Additional connectivity  can be leveraged by using the Camel Integration Capability for Wanaku,
which provides access to more than 300 components.


| Type         | Service Tool                                                                 | Description                                                                 |
|--------------|------------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| `exec`       | [wanaku-tool-service-exec](./wanaku-tool-service-exec/README.md)             | Executes a process as a tool (use carefully - there's no input validation)  |
| `http`       | [wanaku-tool-service-http](./wanaku-tool-service-http/README.md)             | Provides access to HTTP endpoints as tools via Wanaku                       | | Provides access to AWS SQS queues as tools via Wanaku                       |
| `tavily`     | [wanaku-tool-service-tavily](./wanaku-tool-service-tavily/README.md)         | Provides search capabilities on the Web using [Tavily](https://tavily.com/) |


> [!NOTE]
> Some services (i.e.; Tavily, S3, etc.) may require API keys and/or other forms of authentication.
> Check the README.md files in each service documentation for more details.
