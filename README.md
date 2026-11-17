# Unstable-Auto-Joiner

This bot periodically checks discord for new events and sends a signal to the bot, which automatically sprints to the corresponding player model. Made for version 1.21.11 Anti-afk options, like strafing exist.


## WARNING: Use at your own risk. This mod can easily get you banned.


## Prerequisites:
Minecraft Java Edition 1.21.11 with a Fabric loader

Fabric API for 1.21.11

The ClickBot jar from the Releases page

OPSec mod (not required but highly recommended)
get it here: https://github.com/aurickk/OpSec/releases


## How to make the discord watcher: (You need your own message forwarder)

Go to the Discord Developer Portal -> New Application.
Open the Bot tab -> Reset Token -> copy the token (you only see it once).
On the same tab, turn on Message Content Intent and save.
Open OAuth2 -> URL Generator, tick scope bot, tick permissions View Channels and Read Message History, and open the generated link to add the bot to your server. If it is someone else's server, send them the link so an admin can add it.
In Discord, turn on Settings -> Advanced -> Developer Mode, then right-click the channel to watch and choose Copy Channel ID.
First-time setup in game
Join the server and press F9 to open the ClickBot menu.
Paste the bot token into the first box and press Save token.
Paste the channel ID into the second box and press Save channel.
The status line should say Discord: built-in.

Your settings are saved. The token is stored as plain text in the instance's config/clickbot.properties, so never share that file or export your instance with it inside.

## Using it
Stand near the models

Press F8. Chat says MASTER ON and then Discord reader connected.

While it waits, your character strafes left and right near one spot, if turned on

When a message with a model name and a letter appears, your character sprint-jumps to that model and clicks it. Then the master switch turns itself off. Press F8 to start the bot again

## Keys and commands

Key / command	What it does

F8 or /clickbot master	Master switch on/off

F9 or /clickbot menu	Open the menu

/clickbot activate / deactivate	Master switch on / off

/clickbot status	Show what is on

/clickbot strafe on/off	Strafe while waiting

/clickbot sprintjump on/off	Sprint-jump to the model

/clickbot afk on/off	Step forward and back every 30 seconds (strafe overrides it)

/clickbot test wemmbu e	Try a target without Discord (master switch must be on)

/clickbot list	Save nearby entities to clickbot-list.txt for troubleshooting

/clickbot setchannel <id>	Change the Discord channel

/clickbot cleartoken	Forget the saved bot token

/clickbot menukey <name> / masterkey <name>	Change the two shortcut keys (F-keys, Insert, Home, Right Shift, ...)

If a key does nothing (some laptops need Fn + F9), type /clickbot menu, then use the Menu key button in the menu to pick a different key.

## Build from source

You only need this if you want to build the jar yourself. You need Java 25 (for example Temurin 25).

git clone https://github.com/YOUR-USERNAME/clickbot.git

cd clickbot

./gradlew build          # Windows PowerShell: .\gradlew build

The jar is in build/libs/. Use the one without sources or dev in its name.

## Troubleshooting
No /clickbot commands:	The mod did not load. Check that Fabric API and ClickBot are both enabled in the Mods tab

"Discord rejected the bot token"	Paste the token again in the menu

"The bot cannot see that channel"	Give the bot View Channels and Read Message History there

"Channel not found"	Check the channel ID, and that the bot is in that server

Connected, but messages are ignored	Turn on Message Content Intent in the Developer Portal

"Can't click ..."	The model is not loaded near you. Walk closer and try /clickbot list
