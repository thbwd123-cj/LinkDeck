# SPAKE2 component and corresponding source

Artifact: spake2-android 2.2.1, SHA-256 `8798fb6c04b5d53a6307ed9481e9afe563227abd4e8b9e917c8514fa850071bb`.
Upstream: https://github.com/MuntashirAkon/spake2-java/tree/2.2.1
Native submodule: https://github.com/MuntashirAkon/spake2-c/tree/0d15933e5ba3e662cb01245a7ac0dc9fca3eac31

This distribution uses the Android native backend, not the separate experimental pure-Java implementation. The unmodified Java JNI bindings are in `src`, and the native JNI/CMake/C sources are in `upstream/android/src/main/cpp`. LGPL-3.0 and GPL-3.0 texts and per-file notices are retained. These files, the app source, the test-build signing configuration, and the complete build entry point are provided to enable replacing/relinking the component.

To rebuild a native ABI with an installed Android NDK and CMake:

```
cmake -S upstream/android/src/main/cpp -B out/arm64 \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-21 \
  -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_LIBRARY_OUTPUT_DIRECTORY="$PWD/out/arm64"
cmake --build out/arm64
```

Replace `jni/arm64-v8a/libspake2.so` with the resulting library. Repeat for armeabi-v7a, x86 and x86_64 as needed. Retain JNI class/method names when replacing the native library. Java binding edits can be compiled as an Android library using the supplied binding source plus AndroidX annotations; replace `classes.jar` with its class output. Rebuild the app with `python3 build.py`. The local personal test key supplied by the existing project permits installing a relinked test build over its matching test version. A real publisher should use a separate privately managed release key.

Both supplied 64-bit native libraries have 16 KiB ELF LOAD alignment. This does not replace device testing on each ABI and page-size configuration.
