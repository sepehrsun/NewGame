#!/usr/bin/env bash
# Creates the Google Play *upload* key and a local keystore.properties.
# Keep the .jks file and passwords safe (password manager + offline backup).
# With Play App Signing, a lost upload key can be reset via Play Console support.
set -euo pipefail
cd "$(dirname "$0")/.."

KEYSTORE=${1:-cometleap-upload.jks}
ALIAS=upload

if [ -f "$KEYSTORE" ]; then echo "$KEYSTORE already exists – refusing to overwrite."; exit 1; fi

read -rsp "Choose a keystore password (min 6 chars): " PASS; echo
keytool -genkeypair -v -keystore "$KEYSTORE" -alias "$ALIAS" -keyalg RSA -keysize 4096 \
  -validity 10000 -storepass "$PASS" -keypass "$PASS" -dname "CN=Comet Leap Upload"

cat > keystore.properties <<PROPS
storeFile=$KEYSTORE
storePassword=$PASS
keyAlias=$ALIAS
keyPassword=$PASS
PROPS

echo
echo "Done. keystore.properties and $KEYSTORE are git-ignored."
echo "For GitHub Actions, add these repository secrets:"
echo "  COMETLEAP_KEYSTORE_BASE64  = output of: base64 -w0 $KEYSTORE"
echo "  COMETLEAP_KEYSTORE_PASSWORD, COMETLEAP_KEY_PASSWORD = your password"
echo "  COMETLEAP_KEY_ALIAS        = $ALIAS"
echo
echo "Build the Play bundle with: ./gradlew bundleRelease"
echo "  -> app/build/outputs/bundle/release/app-release.aab"
