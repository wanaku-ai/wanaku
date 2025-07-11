# Wanaku Tool - Tavily

A service that searches on the Web using [Tavily](https://tavily.com/). 
 
This service needs tool-specific configuration when they are being added. Specially, the configuration needs to instruct the 
Camel LangChain4j Web Search component how to behave. 

As such, to include a tool using Tavily, first create a configuration (for instance, named `tavily-configuration.properties`) for it. For instance:

```properties
query.resultType=SNIPPET
query.maxResults=3
```

Then save it in any directory on your system. 

Tavily also needs an API key to work. As such, you can create a file named `tavily-secrets.properties` with the following contents:

```properties
tavily.api.key=<my key goes here>
```

Then, you can add the tool in Wanaku using:

```shell
wanaku tools add -n "tavily-search" --description "Search on the internet using Tavily" --uri "tavily://search" --type tavily --secrets-from-file /path/to/tavily-secrets.properties --configuration-from-file /path/to/tavily-configuration.properties
```
