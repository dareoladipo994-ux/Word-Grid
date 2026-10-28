# WordGrid Local Wi-Fi Multiplayer Refinement Plan

## 1. Architecture & Protocol
- **Discovery (UDP Broadcast/Multicast)**:
  - UDP port 8989 for Room Beacon announcements and listener (`roomName`, `hostName`, `hostIp`, `port`, `appVersion`, `protocolVersion`, `isFull`, `isInMatch`).
- **Connection (TCP)**:
  - Port 8988 for reliable bi-directional framing (newline-delimited JSON).
  - Explicit handshake (`HELLO`, `WELCOME`, `REJECT_FULL`, `REJECT_VERSION`).
  - Strict 2-player capacity: rejects 3rd peer immediately.
  - Heartbeats: `PING` every 3 seconds, 10-second timeout disconnect.
- **Authority & Synchronization**:
  - Host is the authoritative referee.
  - Actions (`PLAY_WORD`, `SWAP`, `PASS`, `RESIGN`) carry UUIDs and revision numbers.
  - State revisions are monotonic; guest receives sanitized updates (only their rack + public board/scores/bag count).
  - Draft placements remain private to the player.

## 2. In-Memory Only & Non-Persistence
- `MatchRepository`: Refuse saving `GameMode.LOCAL_WIFI` matches to disk.
- Clean legacy saved Wi-Fi matches with a one-time notice.
- Preserve full save/resume for Computer and Pass & Play.

## 3. Disconnection & Intentional Leaving
- Intercept Back and Home actions in Local Wi-Fi mode with confirmation dialog:
  *"Leaving will end this match for both players. Progress will not be saved."*
- Connection drop / heartbeat timeout displays:
  *"Connection ended. This match has ended. Reconnect to start a new game."*
- Disconnect permanently clears in-memory session.

## 4. UI Flow & Lobby
- Local Wi-Fi Room Flow:
  - Create Room (Host) & Join Room (Discovery list + Manual IP fallback).
  - Lobby Screen: Room name, both player names, ready toggles, Leave Room, Start Match (host only, when both ready).
  - Connection/leave alerts and clean navigation.
