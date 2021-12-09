package com.demo.deploy4;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Camera;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private final int inputWidth = 320;
    private final int inputHeight = 320;
    private static int NUM_THREAD = 4;

    private float[] mean = new float[]{127.5f, 127.5f, 127.5f};
    private float[] normal = new float[]{0.017f, 0.017f, 0.017f};;

    private ExecutorService cameraExecutor;
    private PreviewView viewFinder;
    private ImageView imageView;
    private ImageView background;
    private ImageView none;
    private ImageView backgroundImage1;
    private ImageView backgroundImage2;
    private ImageView backgroundImage3;
    private Bitmap imageNone;
    private Bitmap image1;
    private Bitmap image2;
    private Bitmap image3;
    private Bitmap white;

    private MNNNetInstance instance;

    private boolean inference = false;
    ListenableFuture<ProcessCameraProvider> cameraProviderFuture;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d("life", "oncreate");

        if(!hasPermission()){
            requestPermission();
        }

        background = findViewById(R.id.background);
        viewFinder = findViewById(R.id.viewFinder);
        imageView = findViewById(R.id.output);
        none = findViewById(R.id.none);
        backgroundImage1 = findViewById(R.id.background1);
        backgroundImage2 = findViewById(R.id.background2);
        backgroundImage3 = findViewById(R.id.background3);
        cameraProviderFuture = ProcessCameraProvider.getInstance(MainActivity.this);
        cameraExecutor = Executors.newSingleThreadExecutor();

        try {
            imageNone = BitmapFactory.decodeStream(getAssets().open("none.png"));
            image1 = BitmapFactory.decodeStream(getAssets().open("background5.jpg"));
            image2 = BitmapFactory.decodeStream(getAssets().open("background6.jpg"));
            image3 = BitmapFactory.decodeStream(getAssets().open("background7.jpg"));
            white = BitmapFactory.decodeStream(getAssets().open("background4.jpg"));

            backgroundImage1.setOnClickListener(v -> background.setImageBitmap(image1));
            backgroundImage2.setOnClickListener(v -> background.setImageBitmap(image2));
            backgroundImage3.setOnClickListener(v -> background.setImageBitmap(image3));
            none.setOnClickListener(v -> background.setImageBitmap(white));
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            String modelName = "human_background_remove.MNN";
            //get absolute path of model from asset
            none.setImageBitmap(white);
            backgroundImage1.setImageBitmap(image1);
            backgroundImage2.setImageBitmap(image2);
            backgroundImage3.setImageBitmap(image3);
            background.setImageBitmap(white);
            String modelPath = getCacheDir().getAbsolutePath() + File.separator + modelName;
            Utils.copyFileFromAsset(MainActivity.this, modelName, modelPath);

            //load model
            instance = MNNNetInstance.loadModel(modelPath);

            //create session with inference backend
            instance.createSession(NUM_THREAD, MNNForwardType.FORWARD_VULKAN);


            cameraProviderFuture.addListener(()->{
                try{
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    bindPreview(cameraProvider);
                    bindAnalyser(cameraProvider);

                }catch (ExecutionException | InterruptedException e){

                }
            }, ContextCompat.getMainExecutor(this));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        instance.release();
    }


    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider){

        Preview preview = new Preview.Builder()
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

        cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, preview);
    }


    @SuppressLint("UnsafeExperimentalUsageError")
    private void bindAnalyser(@NonNull ProcessCameraProvider cameraProvider){
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                                            .setTargetResolution(new Size(320, 320))
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build();

        imageAnalysis.setAnalyzer(cameraExecutor, (imageProxy)->{

            if(inference == false){
                imageProxy.close();
                return;
            }


            if(imageProxy.getImage() != null){


                    runOnUiThread(()->{
                            Bitmap input = viewFinder.getBitmap();
                            if(input != null) {
                                Bitmap bitmap = Bitmap.createScaledBitmap(input, inputWidth, inputHeight, false);
                                instance.run(bitmap, mean, normal);
                                imageView.setImageBitmap(bitmap);
                            }
                    });

            }

            imageProxy.close();
        });
        cameraProvider.bindToLifecycle(this,cameraSelector, imageAnalysis);
    }


    @Override
    protected void onPause() {
        super.onPause();
        inference = false;

    }

    @Override
    protected void onResume() {
        super.onResume();
        inference = true;
    }



    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return  checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA
            }, 1);
        }
    }
}