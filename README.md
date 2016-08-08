# Padrone

>*Pa-drone*: a master; boss.  
*Pa*: i.e. dad.  
*Drone*: A person who does tedious or menial work; a drudge.

Master server technology written in Scala that allows users of various platforms (e.g. Steam/Itch/Oculus) to
play a game with each other.

This is software for game developers that want to setup a master server for their
game such that any player that bought their game, regardless of platform, will be able
to host and join games.

## Features

- Login and verification whether the player has bought your game.
- Keeps track of running servers.
- Allows players to query the list of hosts.
- Limits one join session per player. Allows the server to kick the player
  once it assumes multiple sessions are in progress on the same account.
- Automatically sorts hosts based on distance (using Ip to Geo location)
- User ids are never handed out, not to clients nor servers, protecting people's privacy.

## How does it work?

Player A decides to host a server. The game calls `/register-host` providing login details,
an IP endpoint and a host name. The master server checks the login details and
registers the host in its internal state.

Player B decides to join a game. The game calls `/list-hosts` and Player B chooses
one from this list. The game then calls `/join` with the server id and the master
server returns whether the join succeeded. The client then retrieves a session id
that the game has to send to the host.

Once Player B establishes a connection Player A can call `/player-info`
to verify that Player B indeed called `/join` on the Player A host.

## How to install

- Clone the repo and go the `server` folder and build a jar with `sbt assembly`.
- Copy the `application.conf` and fill in your Steam/Itch/Oculus server keys.
- Put the `application.conf` in the same directory as the jar or put it in the
`src/main/resources` folder.
- Start with `java -jar server.jar`
- Make sure to use another webserver to provide HTTPS and redirect the requests
to this server.

## The client

The `csharp_client` folder contains a Unity-based client.

## To be done

- Integrate Steam friends list to allow players to join a friend's game.
- Password protected servers
- Pluggable facilities for match-making

## License

Distributed under the MIT License, see LICENSE.txt for the full text.
