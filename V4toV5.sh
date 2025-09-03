#!/bin/bash

# This script copies the SonicWaveV2 project to SonicWaveV3, updates project references,
# and renames the package to ensure Android Studio can load and test SonicWaveV3 independently.
# Assumptions:
# - The script is executed from the user's home directory or a location where ~/AndroidStudioProjects exists.
# - The existing package name is com.example.sonicwavev1 (based on provided code references).
# - It changes the package to com.example.sonicwavev3 for uniqueness.
# - Run this script in macOS Terminal with bash (e.g., bash copy_v2_to_v3.sh).
# - Uses macOS-compatible sed (with -i '').

# Define paths
#SOURCE_DIR="$HOME/AndroidStudioProjects/SonicWaveV4"
#TARGET_DIR="$HOME/AndroidStudioProjects/SonicWaveV5"

# Copy the directory
#if [ -d "$TARGET_DIR" ]; then
#  echo "Target directory $TARGET_DIR already exists. Aborting to avoid overwrite."
#  exit 1
#fi

#cp -r "$SOURCE_DIR" "$TARGET_DIR"

# Navigate to the new directory
#cd "$TARGET_DIR" || exit 1

# Update root project name in settings.gradle.kts
#  changes every instance of SonicWaveV2 to SonicWaveV3
#in the settings.gradle.kts file:

sed -i '' 's/SonicWaveV5/SonicWaveV4/g' settings.gradle.kts

# Update applicationId and namespace in app/build.gradle.kts
sed -i '' 's/com\.example\.sonicwavev5/com.example.sonicwavev4/g' app/build.gradle.kts

# Rename package directory
if [ -d "app/src/main/java/com/example/sonicwavev4" ]; then
  mkdir -p "app/src/main/java/com/example/sonicwavev5"
cd   ~/AndroidStudioProjects/SonicWaveV4/app/src/main/java/com/example
rm sonicwavev5
mv sonicwavev5 sonicwavev4
else
  echo "Package directory com.example.sonicwavev4 not found. Skipping directory rename."
fi

# Replace package name in all relevant files (Kotlin, XML, Gradle)
find . -type f \( -name "*.kt" -o -name "*.xml" -o -name "*.gradle.kts" \) -exec sed -i '' 's/com\.example\.sonicwavev5/com.example.sonicwavev4/g' {} \;

# Optional: Clean build artifacts to force regeneration in Android Studio
rm -rf .gradle build app/build .idea

echo "SonicWaveV5 created successfully at $TARGET_DIR. Open in Android Studio, sync Gradle, and test."

# End of script
