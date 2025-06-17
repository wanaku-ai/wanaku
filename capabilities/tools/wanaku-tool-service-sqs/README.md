# Wanaku Tool - SQS

The SQS tool allows you to issue requests by posting them to SQS queues. To use this service, follow these steps:

1. Configure the request queue

```shell
wanaku targets tools configure --service=sqs --option=requestQueue --value=wanaku-test-q
```

> [!IMPORTANT]
> Make sure to create the queue on AWS before using it.

2. Configure the response queue:

```shell
wanaku targets tools configure --service=sqs --option=responseQueue --value=wanaku-response-q
```

> [!IMPORTANT]
> Make sure to create the queue on AWS before using it.

3. Set the access key:

```shell
wanaku targets tools configure --service=sqs --option=accessKey --value=<my access key>
```

4. Set the secret key:

```shell
wanaku targets tools configure --service=sqs --option=secretKey --value=<my secret key value>
```

5. Set the region: 

```shell
wanaku targets tools configure --service=sqs --option=region --value=us-east-1
```

6. Create the tool.

```shell
wanaku tools add --name brazilian-barbecue-request --uri "sqs://barbecue"  --description 'Orders the delivery of a an authentic Brazilian barbecue' --type sqs
```

> [!NOTE]
> The URI here is merely cosmetic. The actual queue address and parameters is resolved by the service behind the curtains.

