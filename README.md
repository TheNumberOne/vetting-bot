# Vetting Bot

NOTE THAT THIS BOT IS CURRENTLY A WORK IN PROGRESS.

Vetting Bot is a discord bot meant to be used for vetting new members to discord servers.
Features include
* Custom moderator roles for vetting.
* Custom vetting text.
* For each new user, the bot creates a vetting channel which is destroyed upon completion of verification.
* Previously destroyed vetting channels can be recreated for review or for when a user rejoins a server.
* Administrators can define custom commands for moderators to execute within vetting channels.

## Setup

Run the `v!setup` command to interactively configure the bot for your server.

## Commands
* `v!h[elp] [<command name>] `  
Displays help information for the commands the user can run. A command name can be passed to display help for the
 command.

### Admin Commands
* `v!archive`  
  Lists the users who have been mostly recently vetted.
* `v!archive @user`  
  Recreates the channel that was used to vet the user.
* `v!banwatch`  
  Displays if ban monitoring is enabled.
* `v!banwatch time n`  
  Removes moderator roles from anyone who bans or kicks more than `n` people in the specified amount of `time`.
* `v!category`  
Displays the category that vetting chats are created under.
* `v!category <category>`  
Sets or renames the category that vetting chats are created under. If the category doesn't exist, then it is created.
* `v!command[s] new <command name> arguments...`  
Creates/replaces a command with the name `<command name>` that can only be executed within active vetting channels.
Specifies what the command does when ran. Here are the following options.
    * `+@role` - Adds the specified role to the user.
    * `-@role` - Removes the specified role from the user.
    * `kick reason` - Kicks the user from the server.
    * `ban reason` - Bans the user from the server for the specified reason.
    * `allow @user/@role` - Allows the specific user or role to run the command.
    * `forbid @user/@role` - Forbids the specific user or role from running the command.
* `v!command[s] add <command name> arguments...`  
Adds behavior to a command. The syntax of the arguments are the same as `v!command new`
* `v!command[s] remove <command name>`  
Removes the actions from the command. The syntax is the same as `v!command set`, except that reasons for banning or
 kicking are not specified.
* `v!command[s] delete <command name>`  
Deletes the specified command.
* `v!disable`  
Disables vetting for the server.
* `v!enable`  
Enables vetting for the server.
* `v!mod[s]`  
Lists the roles that can access the vetting channels.
* `v!mod[s] add @role`  
Adds a role to the list of roles that can access the vetting channels.
* `v!mod[s] remove @role`  
Removes a role from the list of roles that can access the verification channels.
* `v!prefix prefix`  
Sets the prefix of the bot to the specified prefix for your server.
* `v!role vetted`  
Displays the role assigned to members after they have been vetted.
* `v!role vetted @role`  
Sets the role assigned to members after they have been vetted.
* `v!role vetting`  
Displays the role assigned to members while they are being vetted.
* `v!role vetting @role`  
Sets the role assigned to members while they are being vetted.
* `v!setup`  
Interactively configures the bot for your server.
* `v!vetmsg`  
Display the message sent to users when they start the vetting process.
* `v!vetmsg message`  
Sets the message sent to users when they start the vetting process. `{message}` is replaced with a mention to the
 user being vetted.
* `v!welcome`  
Lists links to the welcome messages that are used to start the vetting process.
* `v!welcome :reaction: message`  
Creates a welcome message. The specified reaction starts the vetting process for anyone who reacts to it.

### Everyone commands

* `v!invite`  
Creates an invite for adding the bot to a server.
* `v!ping`  
Ensures the bot is still running and returns its latency to Discord.
* `v!source`  
Displays information about the source code of the bot.
  
## Self Hosting (TODO)

On linux, run
```
DISCORD_BOT_TOKEN=<your token> ./gradlew bootRun
```

### Configuration
#### Privileged Gateway Intents
* Server members intent
#### Bot Permissions 
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

## Development (TODO)