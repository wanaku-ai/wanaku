# Wanaku Tool - Tavily

A service that searches on the Web using [Tavily](https://tavily.com/). 

To run set your API key using either one of: 

- the environment variable `TAVILY_API_KEY`
- the `tavily.api.key` property when running the application (i.e.,: `-Dtavily.api.key=my-key`)

```shell
wanaku tools add -n "tavily-search" --description "Search on the internet using Tavily" --uri "tavily://search" --type tavily
```
