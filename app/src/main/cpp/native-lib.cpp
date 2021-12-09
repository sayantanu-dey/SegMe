#include <jni.h>
#include <string>
#include <MNN/ImageProcess.hpp>
#include <MNN/Interpreter.hpp>
#include <MNN/Tensor.hpp>
#include <memory>
#include <android/log.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <iostream>
#include <ctime>
#include <sys/stat.h>
#include <sys/types.h>
#include <dirent.h>


#define TAG "MY_TAG"

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,    TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,     TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,     TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,    TAG, __VA_ARGS__)

#define ui unsigned int

using namespace std;


/*
 * Color helper class to perform basic color operation
 */
class ColorHandler{
public:
    static ui getAlpha(ui color){
        return color>>24;
    }

    static ui getBlue(ui color){
        return (color >> 16) & 0xFF;
    }


    static ui getGreen(ui color){
        return (color >> 8) & 0xFF;
    }

    static ui getRed(ui color){
        return color & 0xFF;
    }

    static ui getARGB(ui alpha, ui red, ui green, ui blue){
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    static ui setAlpha(ui color, ui alpha){
        return (color & 0x00ffffff) | (alpha << 24);
    }

    static int compositeAlpha(int foregroundAlpha, int backgroundAlpha){
        return 0xFF - (((0xFF - backgroundAlpha) * (0xFF - foregroundAlpha)) / 0xFF);
    }

    static int compositeComponent(int fgC, int fgA, int bgC, int bgA, int a){
        if (a == 0) return 0;
        return ((0xFF * fgC * fgA) + (bgC * bgA * (0xFF - fgA))) / (a * 0xFF);
    }

    //add foreground color on backgroung color
    static int compositeColors(int foreground,int background) {
        int bgAlpha = getAlpha(background);
        int fgAlpha = getAlpha(foreground);

        int a = compositeAlpha(fgAlpha, bgAlpha);

        int r = compositeComponent(getRed(foreground), fgAlpha,
                                   getRed(background), bgAlpha, a);
        int g = compositeComponent(getGreen(foreground), fgAlpha,
                                   getGreen(background), bgAlpha, a);
        int b = compositeComponent(getBlue(foreground), fgAlpha,
                                   getBlue(background), bgAlpha, a);

        return getARGB(a, r, g, b);
    }
};



static inline uint64_t getTimeInUs() {
    uint64_t time;
#if defined(_MSC_VER)
    LARGE_INTEGER now, freq;
    QueryPerformanceCounter(&now);
    QueryPerformanceFrequency(&freq);
    uint64_t sec = now.QuadPart / freq.QuadPart;
    uint64_t usec = (now.QuadPart % freq.QuadPart) * 1000000 / freq.QuadPart;
    time = sec * 1000000 + usec;
#else
    struct timeval tv;
    gettimeofday(&tv, nullptr);
    time = static_cast<uint64_t>(tv.tv_sec) * 1000000 + tv.tv_usec;
#endif
    return time;
}


/*
 * load model native function called from java wrapper
 */
extern "C"
JNIEXPORT jlong JNICALL
Java_com_demo_deploy4_MNNNetNative_nativeLoadModel(JNIEnv *env, jclass clazz, jstring modelName_) {

    const char *modelName = env->GetStringUTFChars(modelName_,0);
    auto net = (MNN::Interpreter::createFromFile(modelName));
    env->ReleaseStringUTFChars(modelName_, modelName);

    return (jlong) net;
}


/*
 * release the net pointer
*/
extern "C"
JNIEXPORT jlong JNICALL
Java_com_demo_deploy4_MNNNetNative_nativeReleaseNet(JNIEnv *env, jclass clazz, jlong netPtr) {
    if (0 == netPtr) {
        return 0;
    }
    delete ((MNN::Interpreter *) netPtr);
    return 0;

}


/*
 * creates a runtime session
 */
extern "C"
JNIEXPORT jlong JNICALL
Java_com_demo_deploy4_MNNNetNative_nativeCreateSession(JNIEnv *env, jclass clazz, jlong netPtr, jint numThread, jint forward_type) {

    MNN::ScheduleConfig config;
    config.numThread = numThread;
    config.type      = static_cast<MNNForwardType>(forward_type);
    MNN::BackendConfig backendConfig;
    backendConfig.precision = (MNN::BackendConfig::PrecisionMode)2;
    backendConfig.power = MNN::BackendConfig::Power_High;
    config.backendConfig = &backendConfig;

    auto net = (MNN::Interpreter *)netPtr;
    MNN::Session* session = net->createSession(config);
    net->releaseModel();

    return (jlong) session;

}



/*
 * releases a runtime session
 */
extern "C"
JNIEXPORT void JNICALL
Java_com_demo_deploy4_MNNNetNative_nativeReleaseSession(JNIEnv *env, jclass clazz, jlong netPtr,
                                                  jlong sessionPtr) {
    auto net = (MNN::Interpreter *) netPtr;
    auto session = (MNN::Session *) sessionPtr;
    net->releaseSession(session);
}


/*
 * runs inference and process the bitmap
 */
extern "C"
JNIEXPORT void JNICALL
Java_com_demo_deploy4_MNNNetNative_nativeInference(JNIEnv *env, jclass clazz, jlong netPtr,
                                                   jlong sessionPtr, jobject bitmap,
                                                   jfloatArray mean_, jfloatArray normal_) {



    //start
    auto timeBegin = getTimeInUs();

    auto net = (MNN::Interpreter *) netPtr;
    auto session = (MNN::Session *) sessionPtr;

    //bitmap handle
    AndroidBitmapInfo bitmapInfo;
    AndroidBitmap_getInfo(env, bitmap, &bitmapInfo);
    MNN::CV::ImageProcess::Config config;
    config.destFormat = (MNN::CV::ImageFormat)MNN::CV::ImageFormat::RGB;


    switch (bitmapInfo.format) {
        case ANDROID_BITMAP_FORMAT_RGBA_8888:
            config.sourceFormat = MNN::CV::RGBA;
            break;
        case ANDROID_BITMAP_FORMAT_A_8:
            config.sourceFormat = MNN::CV::GRAY;
            break;
        default:
            MNN_ERROR("Don't support bitmap type: %d\n", bitmapInfo.format);
            return;
    }

    jfloat *mean = env->GetFloatArrayElements(mean_, NULL);
    jfloat *normal = env->GetFloatArrayElements(normal_, NULL);
    ::memcpy(config.mean, mean, 3 * sizeof(float));
    ::memcpy(config.normal, normal, 3 * sizeof(float));

    env->ReleaseFloatArrayElements(mean_, mean, 0);
    env->ReleaseFloatArrayElements(normal_, normal, 0);

    MNN::CV::ImageProcess* process(MNN::CV::ImageProcess::create(config));


    //fetch the inputs and output tensors
    MNN::Tensor* input  = net->getSessionInput(session, NULL);
    MNN::Tensor* output = net->getSessionOutput(session, NULL);

    //tensor to store the bitmap pixels
    MNN::Tensor* inputBitmap = new MNN::Tensor(input, MNN::Tensor::DimensionType::CAFFE);


    //convert bitmap to tensor
    void *pixels = nullptr;

    AndroidBitmap_lockPixels(env, bitmap ,&pixels);
    process->convert((const unsigned char *) pixels, bitmapInfo.width, bitmapInfo.height, 0,inputBitmap);
    AndroidBitmap_unlockPixels(env, bitmap);



    input->copyFromHostTensor(inputBitmap);

    //inference
    net->runSession(session);

    MNN::Tensor* outputMasks = new MNN::Tensor(output);

    output->copyToHostTensor(outputMasks);


    int classes = 1;
    int inputHeight  = bitmapInfo.height;
    int inputWidth = bitmapInfo.width;
    int total = classes * inputHeight * inputWidth;


    auto mask = env->NewFloatArray(total);

    auto tmpPtr = env->GetFloatArrayElements(mask, nullptr);
    ::memcpy(tmpPtr, outputMasks->host<float>(), total * sizeof(float));

    env->ReleaseFloatArrayElements(mask, tmpPtr, 0);


    //post processing the bitmap

    AndroidBitmap_lockPixels(env, bitmap ,&pixels);
    uint32_t* newBitmapPixel = (uint32_t*)pixels;

    for(int y = 0 ; y < inputHeight; y++){
        for(int x = 0 ; x < inputWidth; x++){
            int ind = y*inputWidth + x;
            float maskval = tmpPtr[ind];
            ui alpha = maskval * 255;
            if(alpha < 185) {
                newBitmapPixel[ind] = 0x00000000;
            }
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);

    auto timeEnd = getTimeInUs();

    int time = (timeEnd - timeBegin) / 1000.0;

    LOGI("Time inference %8.3f ms", time);

}