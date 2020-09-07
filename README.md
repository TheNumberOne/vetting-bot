# Vetting Bot

NOTE THAT THIS BOT IS CURRENTLY A WORK IN PROGRESS.

Vetting Bot is a discord bot meant to be used for vetting new members to discord servers.
Features include
* Custom moderator roles for vetting.
* Custom vetting text.
* For each new user, the bot creates a vetting channel which is destroyed upon completion of verification.
* Previously destroyed vetting channels can be recreated for review or for when a user rejoins a server.
* Administrators can define custom commands for moderators to execute within vetting channels.

## Quick Example

Change the prefix to something that works with the other bots.
```shell script
v!prefix v!
```

Set the welcome message.

```
v!welcome
Welcome to the server <member>!
Please follow these instructions to verify: 1. 2. 3
```

Add a command to verify.

```
v!command new verify Verifies the user.
```

```
v!command set verify
+@verified
ping #welcome Welcome to the verified part of the server <member> ^-^.
```

Add a command to kick.

```
v!command new reject Kicks users that vail the verification process.
```

```
v!command set reject kick
```

With these commands, you can now verify members at will.

TODO: Add image of the process.

TODO: Add image of verifying users.

## Commands
* **TODO** `!h[elp] [<command name>] `  
Displays help information for the commands the user can run. A command name can be passed to display help for the
 command.

### Admin Commands
* **TODO** `!prefix <prefix>`  
Sets the prefix of the bot to the specified prefix for your server.
* **TODO** `!mod[s] add <role>`  
Adds a role to the list of roles that can access the verification channels in addition to administrators.
* **TODO** `!mod[s] remove <role>`  
Removes a role from the list of roles that can access the verification channels. You cannot remove administrator's
 access through this.
* **TODO** `!mod[s]`  
Lists the roles that can currently access the verification channel in addition to the mod channels.
* **TODO** `!welcome <message>`  
Sets the vet text to the specified message, with \<member\> in the message replaced with a mention of the user that
joined the server. The default is
`Welcome to the server!`
* **TODO** `!welcome`  
Displays the current vetting text
* **TODO** `!category <category>`  
Sets the category or channel that vetting chats are created under. If the category has 50 channels under it, then
further channels will be created below it rather than within it. If a channel is passed, channels are created
directly below it. `first` can be passed to specify that channels are created before all other channels. `last` (default) can
 be passed to specify that channels are created after all other channels. 
* **TODO** `!category`  
Displays the channel or category that vetting chats are created under.
* **TODO** `!command[s] new <command name> <help text>`  
Creates a new command with the name `<command name>` that can only be executed within active vetting channels.
The `<help text>` is displayed when someone runs `!help` for the command.
* **TODO** `!command[s] [update-]set <command name> arguments...`  
Specifies what the command does when ran. Here are the following options.
    * `+@role`: Adds the specified role to the user.
    * `-@role`: Removes the specified role from the user.
    * `kick`: Kicks the user from the server.
    * `ban <reason>`: Bans the user from the server for the specified reason.
    * `ping #channel <message>`: Pings the user in the specified channel with the specified message. <user> is
     replaced with a mention of the user.
    * `archive`: Archives and deletes the channel when the command is ran. This is default behavior unless `no-archive
    ` or `no-delete
    ` is passed to `!command set` or it is removed with `!command update-remove`.
    * `no-archive`: Can be passed to `!command set` to prevent the bot from archiving the channel upon removal.
    * `delete`: Deletes the channel when the command is ran. This is default behavior unless `no-delete` is passed to
     `!command set` or it is removed with `!command update-remove`.
    * `no-delete`: Can be passed to `!command set` to prevent the channel from being deleted.
* **TODO** `!command[s] update[-add] <command name> arguments...`  
Adds the actions to the command. The syntax is the same as `!command set`, except that neither `no-archive` nor `no
-delete` can be specified.
* **TODO** `!command[s] update-remove <command name>`  
Removes the actions from the command. The syntax is the same as `!command set`, except that neither `no-archive` nor
`no-delete` can be specified. If `delete` is passed, then `archive` is automatically removed. Finally, the ban
 `<reason>` and ping `<message>` aren't specified. 
* **TODO** `!command[s] delete <command name>`  
Deletes the specified command.
* **TODO** `!command[s] [update-]help <command name> <help text>`  
Changes the help text for the command.
* **TODO** `!archive @user`  
  Recreates the channel that was used to vet the user.
* **TODO** `!delete`  
  Deletes the current review channel. (Can only be executed within the review channel.) The review channel can be
   recreated later.

### Moderator Commands

These commands can be executed by moderators in addition to the custom vetting commands defined by the administrator.

### Everyone commands

* `!ping`  
  Ensures the bot is still running.
  
## Self Hosting (TODO)

On linux, run
```
DISCORD_BOT_TOKEN=<your token> ./gradlew bootRun
```

### Configuration
#### Privileged Gateway Intents
* Server members intent
#### Bot Permissions 
268512342

* Manage Roles
* Manage Channels
* Kick Members
* Ban Members
* View Channels
* Send Messages
* Manage Messages
* Read Message History
* Add Reactions 

## Development (TODO)