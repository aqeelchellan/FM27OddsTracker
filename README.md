# FM27 Odds Tracker (starter Android project)

This is the native Android source project, not a compiled APK.

## Current functionality
- Requests Android screen-capture permission (MediaProjection).
- Runs an ongoing foreground service while monitoring.
- Uses on-device ML Kit text recognition.
- Shows a movable floating overlay.
- Calculates normalized implied probabilities from a detected three-number odds sequence.

## Important limitations
- The odds parser is a first-pass heuristic. It can mistake unrelated decimal numbers for odds and needs tuning against real FM27 screenshots.
- The overlay currently displays detected odds but does not yet have robust field/label matching or confidence scoring.
- Android requires explicit screen-capture approval each time monitoring starts.
- This project has not been compiled or tested on the user's Huawei device.

## Build
Open this folder in Android Studio (recent version), allow Gradle sync, then Build > Build APK(s).
The resulting debug APK is normally at `app/build/outputs/apk/debug/app-debug.apk`.

Target: Android 10+ (min SDK 26). Compile SDK 35.
