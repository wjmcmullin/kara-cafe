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
1. Make sure you have JDK 25+ and Maven installed.
2. For first-time setup (when no SUPER_ADMIN user exists in DB), set bootstrap credentials:
   - `KARA_SUPER_ADMIN_USERNAME`
   - `KARA_SUPER_ADMIN_PASSWORD`
   (or system properties `kara.super-admin.username` / `kara.super-admin.password`)
   Existing databases that already contain a SUPER_ADMIN can start without these values.
   If you provide these values on startup, the matching SUPER_ADMIN account is created (if missing) or its password is synchronized.
3. From the project root:

   ```bash
   mvn clean package
   ```

4. Drop the generated `target/kara-cafe.war` into your servlet container (e.g., Tomcat) or run it with your preferred Java EE server.

## Unit testing and coverage
1. Run tests with the Clover instrumentation and coverage report:

   ```bash
   mvn clean clover:instrument-test test clover:aggregate clover:clover package
   ```

2. The OpenClover plugin instruments the code and generates an HTML coverage report under `target/site/clover/index.html` once the build completes.
3. You can open that report locally to inspect line/branch coverage for your unit tests.

## Accessing the app
- Lobby (login + room management): `http://<host>:<port>/kara-cafe/`
- Room detail (after login or guest join): `http://<host>:<port>/karaoke-room/room.html?id=<roomId>`
- Guest invite landing page: `http://<host>:<port>/karaoke-room/join.html?id=<roomId>&token=<token>`
- API base: `/kara-cafe/api/rooms/*`

## Access from a cellphone on the same Wi-Fi
1. Start the app normally (Docker Compose or your servlet container).
2. Use your PC's LAN IP (not `localhost`) from your phone browser. Example:
   - `http://192.168.1.59:8080/kara-cafe/`
3. Set `KARA_PUBLIC_BASE_URL` to that LAN URL so invite QR/link generation uses phone-reachable host.
4. Make sure your phone is on the same Wi-Fi network as your PC.
5. On Windows, allow inbound TCP `8080` or set the active Wi-Fi network profile to **Private**.

Quick checks on Windows:
- Find LAN IP: `ipconfig`
- Confirm host can reach app: `curl http://<LAN_IP>:8080/kara-cafe/`
- Add firewall rule (PowerShell as Administrator):
  - `New-NetFirewallRule -DisplayName "Kara Cafe 8080" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8080`

## Mobile browser support (iOS + Android)
- Supported targets: **Safari on iOS** and **Chrome on Android** (current versions).
- Desktop usage is preserved; mobile improvements only adjust small-screen/touch behavior.
- Mobile essentials included:
  - Viewport metadata on all app pages
  - Compact mobile nav menu pattern for room/profile/search pages
  - Responsive layout/tap-target refinements in shared styles
  - Input behavior tuned for mobile keyboards (reduced unwanted auto-capitalization/correction)

Suggested smoke test on phone:
1. Open lobby and log in.
2. Create or enter a room.
3. Invite a guest and verify room actions.
4. Open Search Karaoke Videos.
5. Open profile pages and submit one update form.

## Extensionless Room URLs
- All browser navigation now uses clean paths (`/karaoke-room/room` and `/karaoke-room/search`) while the servlet forwards those requests to the existing HTML assets.
- Clicking “Enter room” or “Search karaoke videos” keeps `.html` out of the visible address bar while keeping every existing page intact.

## Karaoke Video Search
- Added a dedicated search page that lists karaoke video files from a server directory.
- Users can browse the configured directory, preview matches, and queue selected files through the existing `/song-request` endpoint.

## Configurable Karaoke Directory
- The controller now determines the media root from the `KARAOKE_VIDEO_DIR` environment variable (defaults to `/karaoke-videos` when unset).
- The `/rooms/{id}/search` endpoint only exposes files from that base location for security; configure `KARAOKE_VIDEO_DIR` on the server to point to your actual video store.

## Logout & Navigation
- Logout redirects and playlist links continue to reuse the stored JWT token and `/karaoke-room` paths so the experience remains consistent.