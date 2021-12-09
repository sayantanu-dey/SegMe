package com.demo.deploy4;

import android.graphics.Bitmap;


/*
 *native functions wrapper
 */
public class MNNNetNative {
    private static final String TAG = MNNNetNative.class.getName();
    static {
        System.loadLibrary("native-lib");
        System.loadLibrary("MNN");
    }

    protected static native long nativeLoadModel(String modelName);
    protected static native long nativeReleaseNet(long netPtr);

    protected static native long nativeCreateSession(long netPtr, int thread, int forwardType);
    protected static native void nativeReleaseSession(long netPtr, long sessionPtr);


    protected static native void nativeInference(long netptr, long sessionPtr ,Bitmap bitmap, float []mean, float []normal);

}
