#!/usr/bin/env bash
# Builds the Linux installer: installers/scaccomatto_<version>_amd64.deb
# Run from this folder:  bash build-installer.sh
set -e
VERSION=$(grep -m1 -oP '(?<=<version>)[^<]+' pom.xml)
echo "== Building Scaccomatto $VERSION"
mvn -q clean package
rm -rf dist && mkdir dist
cp "target/scaccomatto-$VERSION.jar" dist/
mkdir -p installers
rm -f "installers/scaccomatto_${VERSION}_amd64.deb"
echo "== Packing the installer (takes a minute)"
jpackage --type deb \
  --name Scaccomatto --app-version "$VERSION" \
  --vendor "Alekhine Studios Private Limited" \
  --description "Chess, with personality" \
  --input dist --main-jar "scaccomatto-$VERSION.jar" --main-class Main \
  --icon src/main/assets/icon.png \
  --add-modules java.se,jdk.crypto.ec,jdk.unsupported,jdk.localedata,jdk.zipfs \
  --java-options "-Dawt.useSystemAAFontSettings=on" \
  --linux-package-name scaccomatto \
  --linux-shortcut --linux-menu-group Game --linux-app-category games \
  --dest installers
echo "== Done: installers/scaccomatto_${VERSION}_amd64.deb"
