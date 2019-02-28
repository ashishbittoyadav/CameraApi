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
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

public class CameraSetUp {

    private TextureView textureView;
    private Context context;
    private CameraDevice cameraDevice;
    private String cameraId;
    private Size previewSize;
    private Size imageSize;
    private ImageReader imageReader;
    private File imageFile;
    private int deviceOrientation;
    private static final int STATE_PREVIEW = 1;
    private static final int STATE_LOCK = 0;
    private int captureState = STATE_PREVIEW;
    private CaptureRequest.Builder captureRequestBuilder;
    private CameraCaptureSession cameraCaptureSession;
    private HandlerThread backgroundHandler;
    private Handler handler;
    private static SparseIntArray ORIENTATION = new SparseIntArray();

    static {
        ORIENTATION.append(Surface.ROTATION_0, 0);
        ORIENTATION.append(Surface.ROTATION_90, 90);
        ORIENTATION.append(Surface.ROTATION_180, 180);
        ORIENTATION.append(Surface.ROTATION_270, 270);
    }

    public CameraSetUp(Context context, TextureView textureView, int deviceOrientation) {
        this.context = context;
        this.textureView = textureView;
        this.deviceOrientation = deviceOrientation;
    }

    public final ImageReader.OnImageAvailableListener onImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            handler.post(new ImageSaver(reader.acquireLatestImage()));
        }
    };
    public CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        public void process(CaptureResult captureResult) {
            switch (captureState) {
                case STATE_PREVIEW:
                    //TO DO
                    //rendering the camera in the texture view.
                    break;
                case STATE_LOCK:
                    captureState = STATE_PREVIEW;
                    Integer autofocusState = captureResult.get(CaptureResult.CONTROL_AF_STATE);
                    if (autofocusState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED
                    ||autofocusState==CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                        captureImage();

                    }
                    break;
            }
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            process(result);
        }
    };


    public static int deviceRotation(CameraCharacteristics cameraCharacteristics, int orientation) {
        int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        orientation = ORIENTATION.get(sensorOrientation);
        return (sensorOrientation + orientation + 360) % 360;
    }

    public CameraDevice.StateCallback cameraDeviceStateBack = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
        }
    };

    public static class CompareArea implements Comparator<Size> {
        @Override
        public int compare(Size o1, Size o2) {
            return Long.signum((long) o1.getHeight() * o1.getWidth() /
                    (long) o2.getWidth() * o2.getHeight());
        }
    }

    int totalRotation=0;

    public void setUpCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(id);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)
                        == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                totalRotation = deviceRotation(cameraCharacteristics, deviceOrientation);
                boolean checkOrientation = totalRotation == 90 || totalRotation == 270;
                int rotatedWidth = width;
                int rotatedHeight = height;
                if (checkOrientation) {
                    rotatedHeight = width;
                    rotatedWidth = height;
                }
                previewSize = optimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedWidth, rotatedHeight);
                imageSize = optimalSize(map.getOutputSizes(ImageFormat.JPEG), rotatedWidth, rotatedHeight);
                imageReader = (ImageReader) ImageReader.newInstance(imageSize.getWidth()
                        , imageSize.getHeight(), ImageFormat.JPEG, 1);
                imageReader.setOnImageAvailableListener(onImageAvailableListener, handler);
                cameraId = id;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void connectedToCamera() {
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {

            if (ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            cameraManager.openCamera(cameraId, cameraDeviceStateBack, handler);
        }
        catch (Exception e)
        {e.printStackTrace();}
    }

    public void startPreview()
    {
        SurfaceTexture surfaceTexture=textureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(previewSize.getWidth(),previewSize.getHeight());
        Surface previewSurface=new Surface(surfaceTexture);
        try
        {
            captureRequestBuilder=cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewSurface);
            cameraDevice.createCaptureSession(Arrays.asList(previewSurface,imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            cameraCaptureSession=session;
                            try {
                                cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(),null,null);
                            }
                            catch (Exception e){e.printStackTrace();}

                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                        }
                    },null);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * The purpose of ImageSaver class is to do file processing of image in a Thread other than UI.
     */
    public class ImageSaver implements Runnable
    {
        public final Image image;
        public ImageSaver(Image image)
        {
            this.image = image;
        }
        @Override
        public void run() {
            ByteBuffer byteBuffer=image.getPlanes()[0].getBuffer();
            byte[] bytes=new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);

            FileOutputStream fileOutputStream=null;
            try {
                fileOutputStream = new FileOutputStream(imageFile);
                fileOutputStream.write(bytes);
            }catch (Exception e)
            {
                e.printStackTrace();
            }finally {
                image.close();
                try
                {
                    if(fileOutputStream!=null)
                        fileOutputStream.close();
                }
                catch (Exception e){e.printStackTrace();}
            }
        }
    }

    public void captureImage()
    {
        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(imageReader.getSurface());
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION,totalRotation);
            CameraCaptureSession.CaptureCallback captureCallback=new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber);
                    imageFile=createFile();
                    Log.e("Taggg","onCaptureStarted");
                }

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Log.e("Taggg","onCaptureCompleted");
                }
            };
            cameraCaptureSession.capture(captureRequestBuilder.build(),captureCallback,null);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }


    public void closeCamera()
    {
        if(cameraDevice!=null)
        {
            cameraDevice.close();
            cameraDevice=null;
        }
    }

    public void backGroundHandlerThread()
    {
        backgroundHandler=new HandlerThread("cameraThread");
        backgroundHandler.start();
        handler=new Handler(backgroundHandler.getLooper());
    }

    public void stopThread()
    {
        backgroundHandler.quitSafely();
        try
        {
            backgroundHandler.join();
            backgroundHandler=null;
            handler=null;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static Size optimalSize(Size[] choices,int width,int height)
    {
        List<Size> big=new ArrayList<Size>();
        for(Size option: choices)
        {
            if(option.getHeight()== option.getWidth() * height/width &&
                    option.getWidth() >= width && option.getHeight() >= height)
            {
                big.add(option);
            }
        }
        if(big.size()>0)
        {
            return Collections.min(big,new CompareArea());
        }
        else
            return choices[0];
    }
    //creating folder in the external storage and creating a file in it and return the file.
    public File createFile()
    {
        File file=context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        String filename="CAMERA_IMG"+System.currentTimeMillis()+".jpg";
        File image=new File(file,filename);
        return image;
    }
    //initiate the process of capturing the image..
    public void lockFocus()
    {
        captureState=STATE_LOCK;
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
        try {
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), captureCallback, handler);
        }
        catch (Exception e){
            Log.e("tagg",e.getMessage());
        }
        captureImage();
    }
}
