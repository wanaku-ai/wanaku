# Wanaku Tool - Exec

Write the description of the tool here

```shell
wanaku tools add -n "sample-exec" --description "Lists the content of a directory." --uri "/bin/ls {parameter.value('directory')}" --type exec --property "argument:string,The directory to list files" --required "directory"
```
