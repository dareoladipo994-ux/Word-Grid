# WordGrid — Offline Mobile Word-Board Game

WordGrid is a tactile, offline mobile word-board game where a human plays against a balanced, local computer opponent, against a friend on the same device with Pass & Play, or across two devices over offline Local Wi-Fi.

## Version 2 Features

1. **Return Home & Continue Match**:
   - Return to the Home screen at any point during a match via the top header back button or system back gesture.
   - All board tiles, racks, bag contents, scores, turns, move history, and unsubmitted draft placements are safely persisted.
   - Prominent **Continue Match** summary card on Home displays mode, player names, scores, and turn indicator.
   - Continuing resumes directly into the game board ready to play.
   - Leaving during a computer turn cleanly suspends thinking and resumes safely on continuation without double execution.
   - If starting a new game while an unfinished match is active, a confirmation dialog protects against accidental replacement.

2. **Three Computer Difficulty Levels**:
   - **Easy**: Focuses on modest word lengths (3–5 letters) and selects moves in the 30%–60% percentile score range (natural 8–20 point words).
   - **Medium (Balanced)**: The standard V1 balanced vocabulary and natural play style, choosing among top candidate moves.
   - **Hard**: Searches deeper anchor points (up to 36 anchors), aggressively optimizes for high-scoring placements, multipliers, and 7-tile bingos (+50 pts).

3. **Local Two-Player Pass & Play**:
   - Two human players take turns on the same device with custom player names.
   - Full-screen **Turn Handover privacy curtain** ("Pass device to [Next Player]" + "I'm Ready") conceals letter tiles between turns so racks stay private.

4. **Offline Local Wi-Fi Peer-to-Peer Device Connection**:
   - Connect two devices over local Wi-Fi or mobile hotspot without internet or cloud services.
   - **Host Game**: Starts TCP server on port 8988 and displays local IP address.
   - **Join Game**: Connects to host IP address to sync board and moves in real time.

5. **Move History & Score Calculation Breakdown Drawer**:
   - Accessible via the history icon button in the game header.
   - Lists moves in chronological order with turn numbers, player tags, words formed, and running score updates.
   - Expandable breakdown cards show itemized score calculations: each letter's base score, active letter/word multipliers, crossword connections, and Bingo (+50) bonuses (e.g. `(C(3) + A(1) + T(1)×2) = 8 pts`).

6. **Personal Statistics & Match Logs**:
   - Filter chips: All, Computer (All, Easy, Medium, Hard), Pass & Play, Local Wi-Fi.
   - Metric cards: Matches Played, Wins / Losses / Win Rate %, Highest Scoring Word & Word Score, High Game Score & Average Score.
   - Recent 50 match logs with timestamps, mode badges, and final scores.
   - Option to reset statistics with a confirmation dialog.

7. **Safe Save Migration (V1 to V2)**:
   - Incremented save format to `version: 2`.
   - When loading an existing Version 1 save, an automatic backup is created at `wordgrid_saved_match.v1.bak`, and fields are migrated seamlessly with backward-compatible defaults.

## Architecture

- **Engine & Core Rules (`com.example.engine`)**:
  - `DictionaryTrie`: High-performance in-memory prefix trie validating 83,000+ Tournament-legal English words bundled in app assets (`words_en.txt`).
  - `BoardGenerator`: Generates 13×13 boards with 180° rotational symmetry and 32 bonus squares.
  - `RuleValidator`: Validates opening center crossover, orthogonal alignment, lack of gaps, connection to committed tiles, crosswords, and generates detailed mathematical `WordScoreDetail` breakdowns.
  - `ComputerPlayer`: Multi-difficulty AI engine (Easy, Balanced, Hard) running strictly on `Dispatchers.Default`.
- **Data Models (`com.example.data.model`)**:
  - `Tile`: Standard 100-tile English distribution, letter values, blank tile assignment.
  - `BoardCell`: Cell coordinate, bonus category (Letter / Word), multiplier, draft status, and committed state.
  - `MatchState`: Full snapshot of match progress, game modes, difficulties, player names, move history, and draft placements.
- **Networking (`com.example.network`)**:
  - `LocalPeerConnection`: Local TCP socket server/client for offline peer-to-peer Wi-Fi gameplay.
- **Persistence (`com.example.persistence`)**:
  - `MatchRepository`: Atomic JSON save/restore to app-private storage with `.v1.bak` backup migration.
  - `StatisticsRepository`: Tracks lifetime game statistics and 50 recent match summaries.
- **UI & Presentation (`com.example.ui`)**:
  - Built 100% with Jetpack Compose (Material 3).
  - Deep navy background (`#0A1124`), slate cards (`#273549`), tactile ivory letter tiles (`#FFFDF2`), electric blue accents (`#2563EB`), sky blue letter bonuses (`#89C4F4`), lavender word bonuses (`#D6B4FC`), and high-multiplier gold (`#F59E0B`).

## Exact Match Rules & Probabilities

### Match Bonus Board Generation
- **Board Size**: 13 rows × 13 columns.
- **Rotational Symmetry**: Every bonus at `(r, c)` is mirrored at `(12 - r, 12 - c)`.
- **Center**: The square at `(6, 6)` is neutral and marked with a starting star glyph.
- **Total Bonuses**: Exactly 32 bonus squares across the board.
- **Match Categories & Probabilities**:
  - **Standard (80%)**: Only 2× and 3× letter and word bonuses.
  - **Boosted (17%)**: Mostly 2× and 3× bonuses, with one mirrored pair of 4× or 5× letter bonuses placed outside the central 5×5 area.
  - **Rare (3%)**: Mostly 2× and 3× bonuses, with one mirrored pair of 6× or 7× letter bonuses placed outside the central 5×5 area.
- Multipliers of 4× through 7× apply **strictly to letter bonuses**. Word bonuses are always 2× or 3×.

### Scoring
- Letter multipliers apply to newly placed tiles covering letter bonus squares.
- Word multipliers multiply the entire word and compound together (e.g. 2× and 3× = 6×).
- Blanks score 0 points, but when placed on a word multiplier, they still activate the multiplier.
- Using all 7 rack tiles in a single turn awards a **+50 point Bingo bonus**.
- Bonuses are consumed upon being covered; subsequent moves bridging existing tiles score normal face values.

### Match Ending
1. The tile bag is empty and one player clears their rack; or
2. 6 consecutive passes or swaps occur; or
3. A player resigns.

At regular completion, unplayed rack values are deducted from each player's score. If a player cleared their rack, the opponent's remaining rack value is also awarded to that player.

## License & Attribution

- English Dictionary: Derived from the Enhanced North American Benchmark Lexicon (ENABLE), dedicated to the public domain by Keith Pomakis (1997). See `app/src/main/assets/DICTIONARY_LICENSE.txt`.
