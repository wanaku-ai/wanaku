# Wanaku Tools - YAML Routes

Provides access to Camel routes in the YAML DSL language as tools to Wanaku.

To run a YAML route using this component, export it like this:

```shell
wanaku tools add -n "my-tool-name" --description "Description of my route" --uri "file:///path/to/my/route.camel.yaml" --type camel-yaml --property "wanaku_body:string,the data to be passed to the route"
```