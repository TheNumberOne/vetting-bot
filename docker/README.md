# Self Hosting

## Discord Configuration
### Privileged Gateway Intents
* Server members intent
### Bot Permissions 
805694678

* View Audit Log
* Manage Roles
* Manage Channels
* Kick Members
* Ban Members
* Manage Webhooks
* View Channels
* Send Messages
* Manage Messages
* Embed Links
* Attach Files
* Read Message History
* Use External Emojis
* Add Reactions 

## Docker

A docker image of the latest release of this bot can be found at `thenumberone/vettingbot`.

For easy self hosting of the bot, first create the bot application in Discord's developer tools.
Then, rename `example-bot.env` to `bot.env` and `example-db.env` to `db.env`. Make sure to modify 
`bot.env` by:
 * setting `DISCORD_BOT_TOKEN` to the token created in Discord's developer tools.
 * setting `OWNER_ID` to the id of your discord account.

After that is completed, run `docker-compose up`