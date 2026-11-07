#!/bin/bash
# Builds the launcher and installs it on the TV.
#   ./deploy.sh                 build and install
#   ./deploy.sh --open          also open it on the TV
#   TV_IP=192.168.0.42 ./deploy.sh    use this if the router gave the TV a new address
set -e
cd "$(dirname "$0")"

TV_IP="${TV_IP:-192.168.0.160}"
TV="$TV_IP:5555"
ADB=~/platform-tools/adb
export ANDROID_HOME=~/Library/Android/sdk
export JAVA_HOME=$(ls -d ~/Library/Java/JavaVirtualMachines/*/Contents/Home | head -1)

./gradlew -q :app:assembleRelease

"$ADB" connect "$TV" >/dev/null
"$ADB" -s "$TV" install -r app/build/outputs/apk/release/app-release.apk

if [ "$1" = "--open" ]; then
    "$ADB" -s "$TV" shell am start -n dev.srujnil.launcher/.MainActivity
fi
