# Available Tools Services

The following tools services can be made available using Wanaku and used to provide access to specific services.

| Type         | Service Tool                                                                                 | Description                                                                 |
|--------------|----------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| `exec`       | [wanaku-tool-service-exec](../services/tools/wanaku-tool-service-exec/README.md)             | Executes a process as a tool (use carefully - there's no input validation)  |
| `http`       | [wanaku-tool-service-http](../services/tools/wanaku-tool-service-http/README.md)             | Provides access to HTTP endpoints as tools via Wanaku                       |
| `kafka`      | [wanaku-tool-service-kafka](../services/tools/wanaku-tool-service-kafka/README.md)           | Provides access to Kafka topics as tools via Wanaku                         |
| `tavily`     | [wanaku-tool-service-tavily](../services/tools/wanaku-tool-service-tavily/README.md)         | Provides search capabilities on the Web using [Tavily](https://tavily.com/) |
| `yaml-route` | [wanaku-tool-service-yaml-route](../services/tools/wanaku-tool-service-yaml-route/README.md) | Provides access to Camel routes in YAML tools via Wanaku                    |

> [!NOTE]
> Some services (i.e.; Tavily, S3, etc.) may require API keys and/or other forms of authentication.
> Check the README.md files in each service documentation for more details.