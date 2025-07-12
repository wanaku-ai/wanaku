# Wanaku Tools - HTTP Service

Provides access to HTTP endpoints as tools to Wanaku.


Headers can be configured using properties, such as: 

```properties
header.CamelHttpMethod=GET
```


Query can be configured using properties, such as:

```properties
query.key=value
```

As such, a file consisting of: 

```properties
header.CamelHttpMethod=GET
query.key=value
```

If configured for a tool such as: 

```shell
wanaku tools add -n "some-tool" --description "Description" --uri "http://host:9096/api/" --type http --configure-from-file /path/to/some-tool-configuration.properties
```

Will generate a GET request to an endpoint consisted of `http://host:9096/api?key=value` when invoked.