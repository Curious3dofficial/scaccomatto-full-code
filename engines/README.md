# engines

Scaccomatto's opponent is **Argus**, a chess engine written from scratch in Java and
compiled into the application. Nothing needs to be downloaded; the bots work out of the box.

## What is in here

`argus.jar` — Argus as a standalone UCI engine. The app does **not** use this file; it
calls Argus directly as Java, in-process, which is faster and means there is no
subprocess to manage. The jar is here so the engine can also be run on its own:

```sh
java -jar argus.jar                 # UCI mode, for Arena / CuteChess / BanksiaGUI
java -jar argus.jar test            # correctness checks
java -jar argus.jar bench 11        # fixed-depth benchmark
java -jar argus.jar perft 6         # move generation node counts
```

## The opening book

`argus.book` is Argus's opening book, and `book-source.pgn` is what it was compiled
from — about forty main lines, twenty ply deep. The engine consults it before searching,
so the first several moves are instant and sound. It matters most for the weaker bots:
without a book, Gary (the 300 bot) cannot search far enough for a centre pawn to pay off and
opens with things like 1.g4. With one he plays the French.

Moves are weighted by how often they appear, so the engine follows main lines most of
the time and occasionally takes a sideline rather than repeating one game forever.

Build your own from any PGN — a master database, games from Lichess, or your own games
out of Scaccomatto, which would make the engine play the openings you actually meet:

```sh
java -jar argus.jar book build argus.book 20 mygames.pgn
java -jar argus.jar book show argus.book            # what it plays as White
java -jar argus.jar book show argus.book e2e4 c7c5  # ... and in the Sicilian
```

The format is deliberately not Polyglot. Polyglot is keyed on its own hash built from 781
published constants, and one mistranscribed digit gives you a book that silently never
matches anything. This one is keyed on the Zobrist hash the engine already maintains, so
a lookup is a single map probe on a number the board is carrying anyway.

## Where the source lives

Two copies, deliberately:

- `src/main/java/argus/**` — the copy Maven compiles into the app. This is what actually
  runs when you play a bot.
- `~/Documents/Argus` — where Argus is developed, with its own build script and tests.

To pull a newer Argus into the app, rebuild it there and copy `src/main/java/argus` across,
then rebuild `argus.jar` into this folder.

## How strong is it

Argus plays at roughly 2200, estimated rather than measured. `ChessBot` treats that as a
hard ceiling: bots rated above it are shown but locked, rather than carrying a number the
engine cannot back up.
