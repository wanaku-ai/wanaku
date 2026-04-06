# Wanaku Tool - Exec

Write the description of the tool here

This service now requires an explicit allowlist of executable paths via `wanaku.service.exec.allowed-executables`.
Only commands whose first token matches one of the configured executables will run.

Sample run for any tool:

```shell
wanaku tools add -n "sample-exec" --description "Lists the content of a directory." --uri "/bin/ls {parameter.value('directory')}" --type exec --property "argument:string,The directory to list files" --required "directory"
```

To run Camel JBang:

```shell
wanaku tools add -n "camel-jbang" --description "Issue a new laptop order" --uri "${HOME}/.jbang/bin/camel run --max-messages=1 ${HOME}/code/java/wanaku/samples/routes/camel-route/camel-jbang-quote.camel.yaml" --type exec
```
