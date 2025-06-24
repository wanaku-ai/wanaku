# Wanaku Tools - YAML Routes

Provides access to Camel routes in the YAML DSL language as tools to Wanaku.

To run a YAML route using this component, export it like this:

```shell
wanaku tools add -n "my-tool-name" --description "Description of my route" --uri "file:///path/to/my/route.camel.yaml" --type camel-yaml
```

## Running Camel Routes as Tools

You can design the routes visually, using [Kaoto](https://kaoto.io/). You need to make sure that the start endpoint for the
route is `direct:start`. If in doubt, check the [hello-quote.camel.yaml](../tests/data/routes/camel-route/hello-quote.camel.yaml)
file in the `samples` directory.
