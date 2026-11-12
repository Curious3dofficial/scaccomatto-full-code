#!/usr/bin/env bash
# Sends your current Argus code to everyone with the full game. Their game picks it up
# quietly the next time it opens. (Scaccomatto Free never changes its Argus.)
#
# Run from this folder:   bash publish-argus.sh
# Try it without sending: bash publish-argus.sh --no-upload
set -e
REPO="Curious3dofficial/scaccomatto-free-site"
TAG="engine"
WORK="target/argus-publish"

rm -rf "$WORK" && mkdir -p "$WORK/classes" "$WORK/res/assets/engine" "$WORK/out"

echo "== Building Argus"
javac --release 17 -nowarn -d "$WORK/classes" $(find src/main/java/argus -name '*.java')
cp src/main/assets/engine/argus.book "$WORK/res/assets/engine/"
jar --create --file "$WORK/argus.jar" --main-class argus.Argus -C "$WORK/classes" . -C "$WORK/res" assets

echo "== Quick check: can it find a move?"
REPLY=$(printf 'uci\nisready\nposition startpos moves e2e4\ngo depth 5\n' | timeout 60 java -jar "$WORK/argus.jar" | grep -m1 '^bestmove' || true)
if [ -z "$REPLY" ]; then echo "Argus didn't answer with a move -- not publishing."; exit 1; fi
echo "   $REPLY"

echo "== Signing"
VERSION=$(java tools/ArgusPublish.java sign "$WORK/argus.jar" "$WORK/out")
echo "   version $VERSION"

if [ "$1" = "--no-upload" ]; then
  echo "== Not uploading (--no-upload). Files are in $WORK/out"
  exit 0
fi

echo "== Uploading"
if ! gh release view "$TAG" -R "$REPO" >/dev/null 2>&1; then
  # a pre-release, so it never becomes the "latest" download on the website
  gh release create "$TAG" -R "$REPO" --prerelease --title "Argus engine updates" \
    --notes "Engine updates for the full game. The game downloads these by itself; nothing to do here."
fi
gh release upload "$TAG" -R "$REPO" "$WORK/out/argus-$VERSION.jar"
gh release upload "$TAG" -R "$REPO" "$WORK/out/argus.json" --clobber

cp "$WORK/argus.jar" engines/argus.jar
echo "== Done. Argus $VERSION is out; games pick it up the next time they open."
echo "   Commit src/main/assets/engine/argus.version so the next app build knows it already has this one."
