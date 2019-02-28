package com.headspire.cameraapi;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private TextureView mTextureView;
    private Button capture;
    private ImageView imageContainer;
    private static final int REQUEST_CAMERA_PERMISSION=2;

    private CameraSetUp cameraSetUp=null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextureView=findViewById(R.id.camerapreview);
        capture=findViewById(R.id.takepicture);
        imageContainer=findViewById(R.id.imagecontainer);
        capture.setOnClickListener(this);
        int deviceOrientation=getWindowManager().getDefaultDisplay().getRotation();
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},REQUEST_CAMERA_PERMISSION);
        }
        cameraSetUp=new CameraSetUp(MainActivity.this,mTextureView,deviceOrientation);
    }
    private TextureView.SurfaceTextureListener surfaceTextureListener=new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            cameraSetUp.setUpCamera(width,height);
            cameraSetUp.connectedToCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    @Override
    public void onClick(View v) {
        switch (v.getId())
        {
            case R.id.takepicture:
                cameraSetUp.lockFocus();
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        cameraSetUp.backGroundHandlerThread();
        if(mTextureView.isAvailable())
        {
            cameraSetUp.setUpCamera(mTextureView.getWidth(),mTextureView.getHeight());
            cameraSetUp.connectedToCamera();
        }
        else
        {
            mTextureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        cameraSetUp.closeCamera();
        cameraSetUp.stopThread();
        super.onPause();
    }
}