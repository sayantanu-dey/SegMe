package com.demo.deploy4;

import android.graphics.Bitmap;
import android.util.Log;

/*
    singleton instance of mnn model
*/
public class MNNNetInstance {
    private static final String TAG = "MY_TAG_JAVA_MNNNET";
    private long mNetInstance = 0;
    private long mSession = 0;

    private MNNNetInstance(long instance){
        this.mNetInstance = instance;
    }

    public static MNNNetInstance loadModel(String fileName) {
        long instance = MNNNetNative.nativeLoadModel(fileName);
        if (instance == 0) {
            Log.e(TAG, "Create Net Failed from file " + fileName);
            return null;
        }

        return new MNNNetInstance(instance);
    }

    public void createSession(int numThread, MNNForwardType forwardType){
        if(mSession != 0){
            throw new RuntimeException("Session Already exists, release it to create new session");
        }

        if(mNetInstance == 0){
            throw new RuntimeException("MNNNetInstance native pointer is null, it may has been released");
        }

        int forward = forwardType.type;

        long session = MNNNetNative.nativeCreateSession(mNetInstance,numThread, forward);
        if(session == 0){
            Log.e(TAG, "Create session failed ");
        }
        this.mSession = session;
    }

    public void releaseSession(){
        if(mNetInstance == 0 || mSession == 0){
            throw new RuntimeException("MNNNetInstance native or session pointer is null, it may has been released");
        }
        MNNNetNative.nativeReleaseSession(mNetInstance, mSession);
        mSession = 0;
    }

    public void run(Bitmap bitmap, float[] mean, float[] normal){
        if(mNetInstance == 0 || mSession == 0){
            throw new RuntimeException("MNNNetInstance native or session pointer is null, it may has been released");
        }
        MNNNetNative.nativeInference(mNetInstance, mSession, bitmap, mean, normal);
    }


    private void checkValid() {
        if (mNetInstance == 0) {
            throw new RuntimeException("MNNNetInstance native pointer is null, it may has been released");
        }
    }

    public void release() {
        checkValid();
        if(mSession != 0){
            releaseSession();
        }

        MNNNetNative.nativeReleaseNet(mNetInstance);

        mNetInstance = 0;
        mSession = 0;
    }


}
