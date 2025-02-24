# Wanaku Tool - Kafka

With this service, it is possible to use Kafka topics as a tool. It is necessary to work in a 
in request/reply mode, so that the record put into a request topic results in a response into a reply one.

The following configurations are available: 

* `bootstrapHost`: to configure the address of the Kafka host.
* `replyToTopic`: to configure the topic where the reply will be sent to the service.
