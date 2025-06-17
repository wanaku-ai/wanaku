# Wanaku Tool - Kafka

With this service, it is possible to use Kafka topics as a tool. It is necessary to work in a 
in request/reply mode, so that the record put into a request topic results in a response into a reply one.

The following configurations are available: 

* `bootstrapHost`: to configure the address of the Kafka host.
* `requestTopic`: to configure the topic where the request will be sent to the service.
* `replyToTopic`: to configure the topic where the reply will be sent to the service.


1. Configure the request topic

```shell
wanaku targets tools configure --service=kafka --option=requestTopic --value=wanaku-request-topic
```

2. Configure the response topic:

```shell
wanaku targets tools configure --service=kafka --option=replyToTopic --value=wanaku-response-topic
```

3. Configure the bootstrap host

```shell
wanaku targets tools configure --service=kafka --option=bootstrapHost --value=my-host:9092
```

6. Create the tool.

```shell
wanaku tools add --name sushi-request --uri "kafka://sushi"  --description 'Orders the delivery of a an authentic Japanese sushi' --property 'wanaku_body:string,All the items you want in your sushi, written in plain text ' --required wanaku_body --type kafka
```

> [!NOTE]
> The URI here is merely cosmetic. The actual topic address and parameters is resolved by the service behind the curtains.

