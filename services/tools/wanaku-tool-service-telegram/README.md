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

## Using the tool

```shell
wanaku tools add --name telegram --uri "telegram://send" --description 'Sends a message  to a user via a TelegramBot' --type telegram
```

## Using the tool to send messages to a particular user

To add the tool to send messages to a particular user, make sure you configure the `telegramId` and then create the tool.

```shell
wanaku targets tools configure --service=telegram --option=telegramId --value=1354733292
```

## Using the tool to send messages to any telegram user

To add the tool to send messages to any telegram user you don't need to configure the `telegramId`, however it must be provided
when invoking the tool (as such, you need to adjust your prompt, so that the `telegramId` is set by the LLM invoking the tool).

