# Contributing

To contribute new features and connectors, read the [Wanaku MCP Router Internals](wanaku-router-internals.md) guide.

If you want to understand what each of the components do, then read the [Wanaku Components and Architecture](architecture.md) guide.


## Creating New Tools

To create a new tool for Wanaku, you can start by creating a new project. For instance, to create one for Kafka:
 
```shell
mvn -B archetype:generate -DarchetypeGroupId=org.wanaku -DarchetypeArtifactId=wanaku-tool-service-archetype   -DarchetypeVersion=1.0.0-SNAPSHOT -DgroupId=org.wanaku -Dpackage=org.wanaku.routing.service -DartifactId=wanaku-routing-kafka-service -Dname=Kafka
```

**NOTE**: this can be used both to create a core tool, part of the Wanaku MCP router project, or to create a custom one for your own needs.

Then, open the `pom.xml` file to add the dependencies for your project. Using the example above, we would include the following dependencies:

```xml
    <dependency>
        <groupId>org.apache.camel.quarkus</groupId>
        <artifactId>camel-quarkus-kafka</artifactId>
    </dependency>
```

Adjust the gPRC port in the `application.properties` file by adjusting the `quarkus.grpc.server.port` property.

**NOTE**: you can also provide the port when launching (i.e., `java -Dquarkus.grpc.server.port=9190 -jar target/quarkus-app/quarkus-run.jar`)

Then, build the project:

```shell
mvn clean package
```

And run it: 

```shell
java -jar target/quarkus-app/quarkus-run.jar
```


After launching, then link the instance 

```shell
wanaku targets tools link --service=kafka --target=localhost:9190
```

**NOTE**: make sure to replace `kafka` with the actual service type you are exposing.

## Building Containers


You can also build containers using: 

```shell
mvn -Pdist -Dquarkus.container-image.build=true -Dquarkus.container-image.push=true clean package
```

For custom containers, please make sure you set the properties `quarkus.container-image.registry` and `quarkus.container-image.group`.
You can do that `pom.xml`:

```xml
<quarkus.container-image.registry>quay.io</quarkus.container-image.registry>
<quarkus.container-image.group>my-group</quarkus.container-image.group>
```

Or in the CLI:

```shell
mvn -Pdist -Dquarkus.container-image.registry=quay.io -Dquarkus.container-image.group=my-group -Dquarkus.container-image.build=true -Dquarkus.container-image.push=true clean package
```
