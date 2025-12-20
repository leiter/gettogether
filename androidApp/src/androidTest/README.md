# JamiBridge Integration Tests

This directory contains instrumentation tests for the JamiBridge interface, which provides the Kotlin/Java layer to communicate with the native Jami daemon via JNI.

## Test Suites

### 1. JamiBridgeDaemonLifecycleTest
Tests daemon initialization, startup, and shutdown operations:
- Daemon initialization
- Starting and stopping the daemon
- Restarting after stop
- Multiple stop calls safety

### 2. JamiBridgeAccountManagementTest
Tests account creation, retrieval, update, and deletion:
- Creating Jami accounts
- Retrieving account details
- Updating account profiles
- Setting account active/inactive state
- Deleting accounts
- Managing multiple accounts

### 3. JamiBridgeDataMarshallingTest
Tests data marshalling across the JNI boundary:
- Simple ASCII strings
- UTF-8 characters (Chinese, Arabic, emojis)
- Special characters
- Empty and very long strings
- Map and List data structures
- Repeated marshalling consistency

## Running the Tests

### Prerequisites
1. Android device or emulator connected
2. Native libraries built for the target architecture (arm64-v8a or x86_64)

### Run All Tests
```bash
./gradlew :androidApp:connectedAndroidTest
```

### Run Specific Test Suite
```bash
# Daemon lifecycle tests
./gradlew :androidApp:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.gettogether.app.bridge.JamiBridgeDaemonLifecycleTest

# Account management tests
./gradlew :androidApp:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.gettogether.app.bridge.JamiBridgeAccountManagementTest

# Data marshalling tests
./gradlew :androidApp:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.gettogether.app.bridge.JamiBridgeDataMarshallingTest
```

### Run Single Test
```bash
./gradlew :androidApp:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.gettogether.app.bridge.JamiBridgeDaemonLifecycleTest#testDaemonInitialization
```

### Check Connected Devices
```bash
adb devices
```

### View Test Results
Test reports are generated at:
- HTML: `androidApp/build/reports/androidTests/connected/index.html`
- XML: `androidApp/build/outputs/androidTest-results/connected/`

## Test Architecture

### Why Instrumentation Tests?
These are instrumentation tests (not unit tests) because they:
1. **Require Android Runtime**: Need the actual Android system to load native `.so` libraries
2. **Use JNI**: The bridge communicates with C++ code via Java Native Interface
3. **Need Real Device/Emulator**: Native library loading requires actual Android environment

### Test Isolation
Each test:
- Creates a unique test data directory
- Initializes a fresh daemon instance
- Cleans up all created accounts after completion
- Deletes test data directory on teardown

This ensures tests don't interfere with each other or with production data.

## Troubleshooting

### Tests Fail with "Library not found"
Ensure native libraries are built for your test device architecture:
```bash
# For x86_64 emulator
./script/build-libjami-x86_64.sh

# For ARM64 device
./script/build-libjami.sh
```

### Tests Timeout
Some tests may take time due to daemon initialization. The tests are marked with `@LargeTest` to allow longer timeouts.

### Daemon Fails to Initialize
Check logcat for native errors:
```bash
adb logcat | grep -i jami
```

## Adding New Tests

When adding new bridge integration tests:

1. **Place in correct package**: `com.gettogether.app.bridge`
2. **Extend test suites**: Add to existing test files or create new ones for new functionality
3. **Use proper annotations**:
   - `@RunWith(AndroidJUnit4::class)` - Use AndroidJUnit4 runner
   - `@LargeTest` - Mark as integration test (allows longer timeout)
4. **Clean up resources**: Always clean up in `@After` to prevent test pollution
5. **Use Truth assertions**: Prefer Google Truth for readable assertions

Example:
```kotlin
@RunWith(AndroidJUnit4::class)
@LargeTest
class JamiBridgeNewFeatureTest {

    private lateinit var bridge: SwigJamiBridge

    @Before
    fun setUp() = runTest {
        // Initialize
    }

    @After
    fun tearDown() = runTest {
        // Clean up
    }

    @Test
    fun testNewFeature() = runTest {
        // Test implementation
    }
}
```

## CI/CD Integration

These tests can be integrated into CI/CD pipelines:

```yaml
# Example GitHub Actions
- name: Run Integration Tests
  uses: reactivecircus/android-emulator-runner@v2
  with:
    api-level: 29
    arch: x86_64
    script: ./gradlew :androidApp:connectedAndroidTest
```
