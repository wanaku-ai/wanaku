# Service Templates

This document explains what service templates are, how they work, and how to use them to create parameterized service catalogs in Wanaku.

> [!NOTE]
> Service templates are parameterized blueprints that produce **service catalogs** when instantiated with user-provided values. For comprehensive documentation on service catalogs themselves — their structure, deployment, and management — see the [Service Catalogs Guide](service-catalogs.md).

## What Are Service Templates?

Service templates are parameterized service catalogs. They allow you to create reusable catalog packages with placeholder values that users can fill in when creating actual service catalogs.

Think of a service template as a blueprint: it defines the structure, routes, and dependencies of a service catalog, but leaves certain values (such as broker URLs, credentials, or topic names) to be filled in later.

## Lifecycle: Template to Service Catalog

The lifecycle of a service template follows this pattern:

1. **Template Creation**: A template is created with parameterized `service.properties` files using Camel Property Placeholders (<code v-pre>{{key}}</code>).
2. **User Fills Parameters**: When instantiating the template, the user provides values for the placeholders.
3. **Service Catalog Creation**: The template is transformed into a deployable service catalog with the user's values substituted in.

```text
Service Template (with {{placeholders}})
    ↓  (user provides values)
Service Catalog (ready to deploy)
```

## How `service.properties` Works with Camel Property Placeholders

Service templates use Camel Property Placeholders to mark values that should be filled in by users. The placeholder syntax is <code v-pre>{{key}}</code>.

Example `service.properties` file in a template:

```properties
broker.url={{broker.url}}
broker.username={{broker.username}}
broker.password={{broker.password}}
queue.name={{queue.name}}
```

When a user instantiates the template, they provide a map of key-value pairs:

```json
{
  "broker.url": "tcp://localhost:61616",
  "broker.username": "admin",
  "broker.password": "admin",
  "queue.name": "my.queue"
}
```

The resulting service catalog will have a `service.properties` file with these values substituted:

```properties
broker.url=tcp://localhost:61616
broker.username=admin
broker.password=admin
queue.name=my.queue
```

## Using Service Templates via CLI

### List Available Templates

```shell
wanaku service template list
```

This displays all service templates available in the router, including their names and descriptions.

You can filter templates by name or description:

```shell
wanaku service template list --search activemq
```

### Instantiate a Template

To create a service catalog from a template, use the `wanaku service template instantiate` command:

```shell
wanaku service template instantiate \
  --name activemq6-tool \
  --property broker.url=tcp://localhost:61616 \
  --property broker.username=admin \
  --property broker.password=admin \
  --property queue.name=my.queue
```

This creates a new service catalog by filling in the template's property placeholders with the provided values.

### Initialize a New Template Locally

You can scaffold a new service template locally using the `wanaku service init` command with the `--template` flag:

```shell
wanaku service init --name my-service --template activemq6-tool
```

This downloads the template from the router and extracts it into a local directory, allowing you to modify it before packaging and deploying.

### Deploy a Template to the Router

To package and deploy a local template directory to the router:

```shell
wanaku service deploy --template /path/to/template
```

This packages the template directory into a ZIP and deploys it to the router's template registry.

## Using Service Templates via UI

The Wanaku Admin UI provides a graphical interface for working with service templates.

### Service Templates Tab

Navigate to the **Service Templates** tab to view all available templates. Each template card shows:

- Template name
- Icon
- Description
- List of services included in the template
- Whether the template has parameterized properties

### Create Service Catalog Wizard

To create a service catalog from a template:

1. Click **Create from Template** on the Service Catalogs page.
2. Select a template from the list.
3. If the template has parameterized properties, a form will appear with input fields for each placeholder.
4. Fill in the values and click **Create**.
5. The new service catalog is created and deployed to the router.

## Creating Custom Templates

You can create your own service templates by following the directory structure and conventions used by built-in templates.

### Directory Structure

A service template ZIP package has this structure:

```text
my-template/
├── index.properties                # Catalog metadata
└── my-service/                     # Service directory (one or more)
    ├── routes.yaml                 # Camel routes definition
    ├── rules.yaml                  # (optional) MCP rules
    ├── dependencies.txt            # (optional) Camel dependencies
    └── service.properties          # (optional) Parameterized properties
```

### `index.properties` Format

The `index.properties` file defines the catalog metadata and declares services:

```properties
# Catalog metadata
catalog.name=my-template
catalog.icon=database
catalog.description=My custom service template

# Service declarations
catalog.services=my-service

# System files (routes, rules, dependencies)
my-service.routes=my-service/routes.yaml
my-service.rules=my-service/rules.yaml
my-service.dependencies=my-service/dependencies.txt

# Declare parameterized properties files
catalog.properties.my-service=my-service/service.properties
```

The `catalog.properties.<system>` key is what marks this as a **template** rather than a regular service catalog. It tells Wanaku that this service has parameterized properties that need to be filled in.

### `service.properties` Format

The `service.properties` file contains key-value pairs with Camel Property Placeholders:

```properties
# Broker connection
broker.url={{broker.url}}
broker.username={{broker.username}}
broker.password={{broker.password}}

# Queue/topic settings
queue.name={{queue.name}}
topic.name={{topic.name}}
```

Use the <code v-pre>{{key}}</code> syntax for any value that should be filled in by the user.

> [!TIP]
> You can also use the **convention-based approach**: if a service has a `service.properties` file at `<service>/service.properties`, Wanaku will automatically detect it even if it's not declared in `index.properties`. This means you can omit the `catalog.properties.<system>` declaration if you follow the convention.

### Packaging the Template

To package your custom template:

```shell
cd my-template/
wanaku service package --output my-template.service.zip
```

This creates a ZIP file suitable for deployment.

### Deploying the Template

To deploy your custom template to the router:

```shell
wanaku service template deploy --file my-template.service.zip
```

Or use the Admin UI to upload the ZIP file via the **Service Templates** → **Upload Template** dialog.

## Built-in Templates

Wanaku ships with several built-in service templates:

### `activemq6-tool`

A template for creating an ActiveMQ 6 (Artemis) tool service. This template includes:

- Routes for sending and receiving messages
- Parameterized broker connection settings
- Queue/topic name placeholders

**Parameters:**

- `broker.url`: ActiveMQ broker URL (e.g., `tcp://localhost:61616`)
- `broker.username`: Broker username
- `broker.password`: Broker password
- `queue.name`: Queue name for message operations

This template is ideal for quickly connecting Wanaku to an ActiveMQ instance without manually writing routes or configuring properties.

### `kafka-tool`

A template for creating a Kafka-backed MCP tool with manual request/reply correlation. This template includes:

- A request route that sends a message to the request topic
- A response route that consumes the response topic and forwards replies into a shared reply queue
- Parameterized brokers, request topic, response topic, reply timeout, and reply consumer group settings

**Parameters:**

- `kafka.brokers`: Kafka bootstrap servers, for example `localhost:9092`
- `kafka.request.topic`: Topic used for outbound requests
- `kafka.response.topic`: Topic used for correlated replies
- `kafka.reply.timeout-ms`: How long to wait for a reply before failing, in milliseconds
- `kafka.response.group-id`: Consumer group used for the reply listener

The request route sets a `wanakuCorrelationId` header from the Camel exchange id, and the response route uses that same header to match the reply to the original request.

### `github-pullrequest-source-tool`

A template for creating a GitHub Pull Request tool service. This template allows agents to fetch pull request information on demand from a specified repository.

**Parameters:**

- `github.owner`: The GitHub repository owner (e.g., `apache`)
- `github.repo`: The GitHub repository name (e.g., `camel`)
- `github.token`: A GitHub Personal Access Token (PAT) for authentication

**Tool Parameters:**

- `state`: Filter PRs by state (`open`, `closed`, `all`). Default: `open`
- `head`: Filter by head user or head organization and branch (`user:branch`)
- `base`: Filter by base branch name
- `sort`: What to sort results by (`created`, `updated`, `popularity`, `long-running`). Default: `created`
- `direction`: The direction of the sort (`asc`, `desc`)

### `sql-tool`

A template for querying a relational database using the [Apache Camel SQL component](https://camel.apache.org/components/next/sql-component.html). This template exposes a SQL query as an MCP tool, allowing AI assistants to fetch live data from any JDBC-compatible database.

**Parameters:**

- `forage.jdbc.username`: Database username
- `forage.jdbc.password`: Database password
- `forage.jdbc.db.kind`: Database type (e.g., `postgresql`, `mysql`). Used to resolve the correct JDBC driver dependencies. If not provided, the template's default is used.
- `sql.query`: The SQL query to execute

**Example:**

```shell
wanaku service template instantiate \
  --name sql-tool \
  --property forage.jdbc.username=postgres \
  --property forage.jdbc.password=wanaku \
  --property sql.query='SELECT name, price FROM products WHERE price < ${body} ORDER BY price' \
  --service-name product-catalog \
  --service-system product-catalog
```

#### Passing Dynamic Arguments to SQL Queries

The `sql-tool` template supports dynamic input from the AI assistant using Camel Simple expressions in the query. The most common pattern is `${body}`, which is replaced at runtime with the input the AI sends when it calls the tool.

For example, with this query:

```sql
SELECT name, price FROM products WHERE price < ${body} ORDER BY price
```

When the AI assistant sends `800` as the tool input, the query executes as:

```sql
SELECT name, price FROM products WHERE price < 800 ORDER BY price
```

Since this template is built on the Apache Camel SQL component, you can use any query pattern that the component supports — including named parameters. For advanced query patterns, refer to the [Camel SQL component documentation](https://camel.apache.org/components/next/sql-component.html).

## Best Practices

### When to Use Templates

- You need to deploy the same service catalog multiple times with different configurations (e.g., dev, staging, prod brokers).
- You want to provide reusable blueprints for common integrations (e.g., Kafka, ActiveMQ, HTTP APIs).
- You want to simplify onboarding by allowing users to fill in a few parameters instead of writing YAML routes.

### When NOT to Use Templates

- The service catalog has no parameterized values (just deploy it as a regular catalog).
- The configuration is one-time and won't be reused.

### Template Naming Conventions

- Use lowercase kebab-case for template names (e.g., `activemq6-tool`, `kafka-producer`).
- Use descriptive names that indicate the integration or service type.

### Property Key Naming

- Use dot-separated keys (e.g., `broker.url`, `queue.name`) to group related properties.
- Avoid overly generic keys like `url` or `name` — prefix them with context (e.g., `broker.url` instead of `url`).

## Troubleshooting

### Template Not Found

If `wanaku service template list` doesn't show your template, check:

- Was the template deployed successfully? Check the router logs for errors.
- Does the ZIP contain a valid `index.properties` file?
- Is the `catalog.properties.<system>` key declared (or does the template follow the convention with `<system>/service.properties`)?

### Properties Not Replaced

If placeholders like <code v-pre>{{broker.url}}</code> are not being replaced:

- Ensure you're using the correct Camel Property Placeholder syntax: <code v-pre>{{key}}</code>, not `${key}`.
- Check that the property key matches exactly what you provided during instantiation.
- Verify the `service.properties` file is correctly declared in `index.properties` or follows the `<system>/service.properties` convention.

### Invalid ZIP Structure

If deployment fails with a ZIP parsing error:

- Ensure the ZIP contains an `index.properties` file at the root.
- Check that service directories match the names declared in `catalog.services`.
- Verify that file paths in `index.properties` match the actual file locations in the ZIP.

## Related Documentation

- [Usage Guide](usage.md) — General Wanaku usage and concepts
- [Configurations](configurations.md) — Quarkus and Wanaku configuration options
- [Contributing](contributing.md) — How to contribute to Wanaku
