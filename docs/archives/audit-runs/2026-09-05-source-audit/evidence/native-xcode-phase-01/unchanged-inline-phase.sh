cd "$SRCROOT/.."
if [ "YES" = "$OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then
  echo "Skipping Gradle build task invocation due to OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED environment variable set to \"YES\""
  exit 0
fi
./gradlew --no-daemon :composeApp:embedAndSignAppleFrameworkForXcode --dependency-verification=strict --console=plain
scripts/release/normalize_embedded_apple_framework.sh "${TARGET_BUILD_DIR}/${FRAMEWORKS_FOLDER_PATH}" ComposeApp
