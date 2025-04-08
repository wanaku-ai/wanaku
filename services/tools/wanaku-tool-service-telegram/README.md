# Wanaku Tool - Telegram

This Tool sends messages to user via Telegram bot.

## Configuration

The following configurations are available:

* `authorizationToken`: to configure the authorization token of the Telegram bot. This configuration is mandatory.
* `telegramId`: to configure the telegramId that receives the message. This configuration to be used only if the MCP tool is sending messages to a defined single user.

Example of configuring the Authorization Token for the Telegram tool:

```shell
wanaku targets tools configure --service=telegram --option=authorizationToken --value=1354733292
```

## Using the tool to send messages to a particular user

To add the tool to send messages to a particular user.

```shell
wanaku tools add  --name telegram --uri "telegram://send-to-alice" --description 'Sends a message to Alice' --property 'message:string, the message to send to the user'  --required=message --type telegram
```

## Using the tool to send messages to any telegram user

To add the tool to send messages to any telegram user.

```shell
wanaku tools add --name telegram --uri "telegram://send" --description 'Sends a message  to a user via a TelegramBot' --property 'telegramId:string, the telegram id of the telegram user' --property 'message:string, the message to send to the user'  --required='telegramId,message' --type telegram
```