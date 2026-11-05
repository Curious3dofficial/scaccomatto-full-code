<p align="center"><img src="docs/img/wordmark.png" alt="Scaccomatto" width="520"></p>

<p align="center"><b>Chess, with personality.</b><br>
A free chess game for Windows and Linux by Alekhine Studios Private Limited.</p>

<p align="center"><a href="../../releases/latest"><b>Download the latest version</b></a></p>

<p align="center"><img src="docs/img/boardroom.jpg" alt="Scaccomatto's Board Room" width="760"></p>

## What's in it

- **Eight variants.** Classic, Chess960, King of the Hill, Three-check, Atomic, Fog of War and Duck Chess, with Spell Chess coming later.
- **Bots with a story.** Each one plays its own way, from Gary, who grabs anything you leave hanging, up to Margot.
- **Analysis and Game Review.** Load a game, step through it, and see which moves were good and which ones lost it.
- **Play a friend.** On the same computer, or on the same Wi-Fi with a six-letter join code.
- **Achievements**, three piece sets, board themes, and light and dark mode.

The engine, **Argus**, was written from scratch for Scaccomatto. It plays every bot and runs the analysis.

## Installing

**Windows:** run `Scaccomatto-Setup-<version>.exe`. If Windows says "Windows protected your PC", click *More info*, then *Run anyway* (the installer isn't signed with a paid certificate yet).

**Linux (Debian, Ubuntu, Mint):**

```bash
sudo apt install ./scaccomatto_<version>_amd64.deb
```

Both installers include their own Java, so there's nothing else to install.

## Building it yourself

You need JDK 17 or newer and Maven.

```bash
mvn package                 # builds target/scaccomatto-<version>.jar
java -jar target/scaccomatto-<version>.jar
bash build-installer.sh     # Linux .deb, in installers/
```

The Windows installer is built by GitHub Actions (`.github/workflows/windows-installer.yml`): run it from the Actions tab, or push a tag like `v1.0.1` to build it and attach it to a release. Before each release, raise the version in `pom.xml` and `AppVersion.java`, otherwise Windows treats the new installer as the same version.

## Project layout

| Folder | What's in it |
|---|---|
| `src/main/java` | The game (Swing). `Main.java` starts it. |
| `src/main/java/argus` | Argus, the chess engine |
| `src/main/assets` | Pictures, sounds, music, fonts, the opening book |
| `src/test/java` | Unit tests (`mvn test`) |
| `server` | The account server, for a future version with accounts |
| `docs` | The download website (GitHub Pages) |
| `packaging` | Installer icon and the Windows build workflow |

## Privacy and credits

Scaccomatto doesn't collect anything; see [docs/privacy.html](docs/privacy.html). Music, sounds, piece sets and fonts by other people are listed in [docs/credits.html](docs/credits.html) and in the app under Settings → About.

## Contact

alekhinestudios@gmail.com

© 2026 Alekhine Studios Private Limited
