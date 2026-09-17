#!/bin/sh
set -eu

: "${CONFIGURATION:?Xcode configuration is required}"
: "${SRCROOT:?Xcode source root is required}"
: "${TARGET_BUILD_DIR:?Xcode target build directory is required}"
: "${UNLOCALIZED_RESOURCES_FOLDER_PATH:?App resource directory is required}"

case "$CONFIGURATION" in
    Debug) : "${APP_DISPLAY_NAME:?Debug display name is required}" ;;
    Release) ;;
    *) echo "Unsupported app metadata configuration: $CONFIGURATION" >&2; exit 2 ;;
esac

# InfoPlist.strings overrides the base plist's display name. Produce each
# localized file exactly once, without editing tracked localization sources.
# Only Debug's CFBundleDisplayName is locale-invariant development branding;
# Store names and every localized privacy purpose string remain unchanged.
for locale in en ar; do
    source="$SRCROOT/iosApp/$locale.lproj/InfoPlist.strings"
    destination="$TARGET_BUILD_DIR/$UNLOCALIZED_RESOURCES_FOLDER_PATH/$locale.lproj/InfoPlist.strings"
    mkdir -p "$(dirname "$destination")"
    /usr/bin/plutil -convert binary1 -o "$destination" "$source"
    if [ "$CONFIGURATION" = Debug ]; then
        /usr/libexec/PlistBuddy -c "Set :CFBundleDisplayName $APP_DISPLAY_NAME" "$destination"
    fi
done
