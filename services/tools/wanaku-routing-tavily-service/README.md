# Wanaku Tool - Tavily

A service that searches on the Web using [Tavily](https://tavily.com/). 

To run set your API key using either one of: 

- the environment variable `TAVILY_API_KEY`
- the `tavily.api.key` property when running the application (i.e.: `-Dtavily.api.key=my-key`)

```shell
wanaku tools add -n "tavily-search" --description "Search on the internet using Tavily" --uri "tavily://search?maxResults={maxResults}" --type tavily --property "_body:string,The search terms" --property "maxResults:int,The maxResults is the expected number of results to be found if the search request were made" --required "_body"
```
