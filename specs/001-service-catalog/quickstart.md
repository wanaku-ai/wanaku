# Quickstart: Service Catalog

## Overview

The service catalog lets you package Camel routes, Wanaku rules, and dependencies into a single deployable ZIP archive. Each archive represents a "service" composed of one or more "systems" (backend integrations).

## Workflow

### 1. Initialize a Service

```bash
wanaku service init --name=finance --services=finance-new,finance-legacy
```

This creates:
```
finance/
├── index.properties
├── finance-new/
│   ├── finance-new.camel.yaml
│   ├── finance-new.wanaku-rules.yaml
│   └── finance-new.dependencies.txt
└── finance-legacy/
    ├── finance-legacy.camel.yaml
    ├── finance-legacy.wanaku-rules.yaml
    └── finance-legacy.dependencies.txt
```

### 2. Define Routes and Rules

Edit the generated Camel route files (`*.camel.yaml`) with your integration routes. Edit the rules files (`*.wanaku-rules.yaml`) to define which routes are exposed as MCP tools/resources.

### 3. Auto-Generate Rules (Optional)

```bash
cd finance
wanaku service expose --path=.
```

Reads the Camel route files, finds all route definitions, and generates corresponding rules files that expose each route as an MCP tool. Optionally specify a namespace:

```bash
wanaku service expose --path=. --namespace=production
```

### 4. Deploy the Service

```bash
cd finance
wanaku service deploy --path=.
```

This:
1. Validates the index.properties and referenced files
2. Creates `finance.service.zip` containing all files
3. Uploads the ZIP to the Wanaku data store with `fileType=CATALOG`

### 5. Use in Camel Integration Capability

```bash
java -jar camel-integration-capability.jar \
  --service-catalog datastore://finance.service.zip
```

The capability downloads the ZIP, extracts the index, and loads each system's routes, rules, and dependencies.

### 6. View in Admin UI

Navigate to the **Service Catalog** page in the Wanaku admin UI to see all deployed services. You can search by name and delete services that are no longer needed.

## Key Concepts

| Concept | Description |
|---------|-------------|
| Service | A named, deployable package grouping one or more systems |
| System | A single backend integration with its own routes, rules, and optional dependencies |
| Index | The `index.properties` manifest listing all systems and their file references |
| Catalog ZIP | The `<name>.service.zip` archive uploaded to the data store |
