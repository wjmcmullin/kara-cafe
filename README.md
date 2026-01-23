## Work In Progress
This project is still under active development. The end goal is to enable you to run your own full karaoke server, complete with durable room persistence, richer guest flows, audio sync, and a polished UI—so consider the current implementation a stepping stone toward that vision.

# Kara Café – Karaoke Web Server

A lightweight Java web app that powers a karaoke lobby + room experience. It’s a work-in-progress prototype intended to let you run your own karaoke server, inviting temporary guests into private rooms.

## What it currently does
- Provides a lobby (`/kara-cafe/karaoke-room/`) where admins and super admins can:
  - Create new rooms
  - Delete rooms they own
  - Invite guests via temporary USER accounts that can only enter a specific room
- Shows room metadata (owner, uptime, occupants) and lets guests request songs
- Offers per-room entry via `/kara-cafe/karaoke-room/room.html?id=<roomId>`
- Secures APIs via token authentication (including invite-only tokens restricted to one room)
- Logs controller/service/authentication events through Log4j 2 for observability

## Build instructions
1. Make sure you have JDK 21+ and Maven installed.
2. From the project root:

   ```bash
   mvn clean package
   ```

3. Drop the generated `target/kara-cafe.war` into your servlet container (e.g., Tomcat) or run it with your preferred Java EE server.

## Unit testing and coverage
1. Run tests with the Clover instrumentation and coverage report:

   ```bash
   mvn clean clover:test-instrument test clover:aggregate clover:clover package
   ```

2. The OpenClover plugin instruments the code and generates an HTML coverage report under `target/site/clover/index.html` once the build completes.
3. You can open that report locally to inspect line/branch coverage for your unit tests.

## Accessing the app
- Lobby (login + room management): `http://<host>:<port>/kara-cafe/`
- Room detail (admin or invite link): `http://<host>:<port>/karaoke-room/room.html?id=<roomId>&token=<token>`
- API base: `/kara-cafe/api/rooms/*`