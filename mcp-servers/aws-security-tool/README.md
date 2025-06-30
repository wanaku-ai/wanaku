# AWS Security Tool MCP Server

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Packaging and running the application

The application can be packaged and installed using:

```shell script
./mvnw install
```

This builds an uber-jar which you can run directly using `jbang com.github.oscerd:aws-security-tool:1.0.0-SNAPSHOT:runner`

To utilize in an MCP client (such as Claude Desktop), you can use the following command:

```shell script
jbang --quiet com.github.oscerd:aws-security-agent:1.0.0-SNAPSHOT:runner 
```

The `--quiet` flag is used to suppress any output of the jbang script.
