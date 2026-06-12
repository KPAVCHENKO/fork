// JNI bridge to rlottie (Samsung's C++ Lottie renderer — the engine Telegram uses
// for TGS stickers). Renders frames into an Android ARGB_8888 Bitmap off the UI
// thread, so animated stickers stay smooth even on weak devices.

#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <rlottie.h>
#include <memory>
#include <string>

namespace {
struct Holder {
    std::unique_ptr<rlottie::Animation> animation;
};
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_app_fork_messenger_media_RLottie_nativeLoad(JNIEnv *env, jclass, jstring json) {
    if (json == nullptr) return 0;
    const char *chars = env->GetStringUTFChars(json, nullptr);
    if (chars == nullptr) return 0;
    auto animation = rlottie::Animation::loadFromData(std::string(chars), std::string(), std::string(), false);
    env->ReleaseStringUTFChars(json, chars);
    if (!animation) return 0;
    auto *holder = new Holder{std::move(animation)};
    return reinterpret_cast<jlong>(holder);
}

JNIEXPORT jint JNICALL
Java_app_fork_messenger_media_RLottie_nativeFrameCount(JNIEnv *, jclass, jlong ptr) {
    auto *holder = reinterpret_cast<Holder *>(ptr);
    return holder ? static_cast<jint>(holder->animation->totalFrame()) : 0;
}

JNIEXPORT jdouble JNICALL
Java_app_fork_messenger_media_RLottie_nativeFrameRate(JNIEnv *, jclass, jlong ptr) {
    auto *holder = reinterpret_cast<Holder *>(ptr);
    return holder ? holder->animation->frameRate() : 0.0;
}

JNIEXPORT void JNICALL
Java_app_fork_messenger_media_RLottie_nativeRender(JNIEnv *env, jclass, jlong ptr,
                                                   jint frame, jobject bitmap, jint w, jint h) {
    auto *holder = reinterpret_cast<Holder *>(ptr);
    if (!holder || bitmap == nullptr || w <= 0 || h <= 0) return;

    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0 || pixels == nullptr) return;

    auto *buffer = static_cast<uint32_t *>(pixels);
    rlottie::Surface surface(buffer, static_cast<size_t>(w), static_cast<size_t>(h),
                             static_cast<size_t>(w) * 4);
    holder->animation->renderSync(static_cast<size_t>(frame), surface);

    // rlottie outputs premultiplied ARGB32 in Cairo order (uint32 0xAARRGGBB), but
    // Android ARGB_8888 expects RGBA in memory (uint32 0xAABBGGRR). Swap R and B.
    const size_t count = static_cast<size_t>(w) * static_cast<size_t>(h);
    for (size_t i = 0; i < count; ++i) {
        const uint32_t c = buffer[i];
        buffer[i] = (c & 0xFF00FF00u) | ((c & 0x00FF0000u) >> 16) | ((c & 0x000000FFu) << 16);
    }
    AndroidBitmap_unlockPixels(env, bitmap);
}

JNIEXPORT void JNICALL
Java_app_fork_messenger_media_RLottie_nativeDestroy(JNIEnv *, jclass, jlong ptr) {
    delete reinterpret_cast<Holder *>(ptr);
}

}
