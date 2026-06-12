# Environment for building TDLib for Android on Windows (Git Bash / MSYS).
# Source this file before running the build scripts:
#   source scripts/tdlib-build-env.sh
#
# Current build: TDLib 1.8.64, tdlib/td commit e0943d068ce90b5010f1aea946e6901e25b43bf6,
# NDK 28.2.13676358, OpenSSL 1.1.1w (static), ABIs: arm64-v8a + x86_64.
# Built with:
#   cd third_party/td/example/android
#   ./build-openssl-2abi.sh "C:/Android" "28.2.13676358" third-party/openssl-ndk28
#   ./build-tdlib-2abi.sh  "C:/Android" "28.2.13676358" third-party/openssl-ndk28
#
# Tools layout (portable, no admin rights needed):
#   C:\tools\msysbin   - GNU make, gperf (from MSYS2 packages) + msys-stdc++ DLLs
#   C:\tools\php       - portable PHP (used by AddIntDef.php post-processing)
#   C:\tools\mingw64   - WinLibs MinGW-w64 GCC (host compiler for TDLib code generators)
#   C:\tools\perl5lib  - perl core modules missing from Git Bash perl (from MSYS2 perl 5.38.2)
#   C:\Android         - Android SDK (NDK 23.2.8568313, CMake 3.22.1 + ninja)

export PERL5LIB="/c/tools/perl5lib/share_core:/c/tools/perl5lib/lib_core"
export PATH="/c/tools/msysbin:/c/tools/php:/c/tools/mingw64/bin:$PATH"

# Host compiler for the TD_GENERATE_SOURCE_FILES step (cmake native build).
export CC=gcc
export CXX=g++
export CMAKE_GENERATOR=Ninja

# GCC 15+/16 defaults to C23, which rejects the K&R-style C in td/generate/tl-parser.
export CFLAGS="-std=gnu17"

export ANDROID_SDK_ROOT_WIN="C:/Android"
export TDLIB_NDK_VERSION="23.2.8568313"

# --- rlottie (native TGS sticker engine, added v0.21.0) ---
# Source cloned into third_party/rlottie (gitignored, like td). To rebuild from a
# fresh checkout:
#   git clone --depth 1 https://github.com/Samsung/rlottie.git third_party/rlottie
# It is compiled by Gradle's externalNativeBuild (app/src/main/cpp/CMakeLists.txt)
# into libforklottie.so for arm64-v8a + x86_64 — no manual build step needed.
