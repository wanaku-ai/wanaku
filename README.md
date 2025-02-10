# Wanaku

## Build native 

```shell
eval $(make prepare)
make cli-native
make install
```


## Expose resources

```shell
java -jar cli/target/quarkus-app/quarkus-run.jar resources expose --location=$HOME/tmp/test-mcp-2.txt --mimeType=text/plain --description="Sample resource added via CLI" --name="test mcp via CLI" --type=file
```