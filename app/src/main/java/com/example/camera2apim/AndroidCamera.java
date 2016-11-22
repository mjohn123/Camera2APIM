package com.example.camera2apim;

import android.Manifest;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaActionSound;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class AndroidCamera extends AppCompatActivity {


    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private static final String TAG = "Camera2App";
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAIT_LOCK = 1;
    private String mImageFileLocation = "";
    private int mSensorOrientation;
    /**
     * Camera state: Picture was taken.
     */
    private int mState;
    private TextureView mTextureView;
    private Size mPreviewSize;

    private String mCameraId;
    String[] PERMISSIONS = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA};
    private static final int PERMISSION_ALL = 105;
    private static final int REQUEST_CAMERA_RESULT = 106;
    KeyguardManager.KeyguardLock kl;
    public ProgressDialog delayProgressDialog;
    private TextureView.SurfaceTextureListener mSurfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    mSurfaceTextureAvailable = true;
                    setupCameraIfPossible();
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    mSurfaceTextureAvailable = false;
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {

                }
            };
    private boolean mSurfaceTextureAvailable;
    private boolean mPermissionsGranted;

    private void setupCameraIfPossible() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (mSurfaceTextureAvailable && mPermissionsGranted) {
                setupCamera(mTextureView.getWidth(), mTextureView.getHeight(), "1");
                openCamera();
            }
        }
        else {
            if (mSurfaceTextureAvailable) {
                setupCamera(mTextureView.getWidth(), mTextureView.getHeight(), "1");
                openCamera();
            }
        }
    }

    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private CameraDevice mCameraDevice;
    private CameraDevice.StateCallback mCameraDeviceStateCallback =
            new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    mCameraOpenCloseLock.release();
                    mCameraDevice = camera;
                    createCameraPreviewSession();
                    delayProgressDialog.dismiss();
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    mCameraOpenCloseLock.release();
                    camera.close();
                    mCameraDevice = null;

                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    mCameraOpenCloseLock.release();
                    camera.close();
                    mCameraDevice = null;
                    //finish();
                }
            };
    private CaptureRequest mPreviewCaptureRequest;
    private CaptureRequest.Builder mPreviewCaptureRequestBuilder;
    private CameraCaptureSession mCameraCaptureSession;
    private CameraCaptureSession.CaptureCallback mSessionCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {

            switch (mState) {
                case STATE_PREVIEW:
                    break;
                case STATE_WAIT_LOCK:
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == CaptureRequest.CONTROL_AF_STATE_FOCUSED_LOCKED) {
                        captureStillImage();
                    } else {
                        captureStillImage();
                    }

                    break;
            }


        }

        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);

            process(result);
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            Handler mHandler = new Handler(getMainLooper());
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "Focus Lock UnSuccesful", Toast.LENGTH_SHORT).show();
                }
            });

        }
    };

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private static File mImageFile;
    private ImageReader mImageReader;
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {

                    mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage()));
                }
            };

    private static class ImageSaver implements Runnable {


        private final Image mImage;

        private ImageSaver(Image image) {
            mImage = image;
        }

        @Override
        public void run() {

            ByteBuffer byteBuffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);

            FileOutputStream fileOutputStream = null;

            try {

                fileOutputStream = new FileOutputStream(mImageFile);
                fileOutputStream.write(bytes);

            } catch (IOException e) {
                e.printStackTrace();
            } finally {

                mImage.close();
                if (fileOutputStream != null) {
                    try {
                        fileOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            }

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera_activity);
        KeyguardManager keyguardManager = (KeyguardManager)getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguardManager.inKeyguardRestrictedInputMode())
        {
            Log.d(TAG,"======******=======unlock=========************");
            Window window = AndroidCamera.this.getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        kl = keyguardManager.newKeyguardLock("MyKeyguardLock");
        kl.disableKeyguard();
        PowerManager pm = (PowerManager) getSystemService(getApplicationContext().POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK
                | PowerManager.ACQUIRE_CAUSES_WAKEUP
                | PowerManager.ON_AFTER_RELEASE, "MyWakeLock");
        wakeLock.acquire();
        Log.d(TAG,"======******=======onCreate=========************");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mPermissionsGranted = hasAllPermissions(this, PERMISSIONS);
            if (!mPermissionsGranted) {
                ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
            }
        }
        mTextureView = (TextureView) findViewById(R.id.texture);

        delayProgressDialog = ProgressDialog.show(AndroidCamera.this, "Please wait ...", "Opening ...", true);
        delayProgressDialog.setCancelable(true);
    }

    public static boolean hasAllPermissions(Context context, String... permissions) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    public void takepicture(View view) {

        try {
            mImageFile = createImageFile();
            Log.d("TAG", "=====Take picture=====");

        } catch (IOException e) {
            e.printStackTrace();
        }

        lockFocus();
        MediaActionSound sound = new MediaActionSound();
        sound.play(MediaActionSound.SHUTTER_CLICK);
    }

    public void switch_camera(View view) {
        closeCamera();
        //swap the id of the camera to be used
        if (mCameraId == String.valueOf(Camera.CameraInfo.CAMERA_FACING_BACK)) {
            mCameraId = String.valueOf(Camera.CameraInfo.CAMERA_FACING_FRONT);
        } else {
            mCameraId = String.valueOf(Camera.CameraInfo.CAMERA_FACING_BACK);
        }
        if (mTextureView.isAvailable()) {

            setupCamera(mTextureView.getWidth(), mTextureView.getHeight(), mCameraId);
            openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }

    }
    public void close_camera(View view) {
        finish(); // call this to finish the current activity
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(homeIntent);
    }
    File createImageFile() throws IOException {

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "Image_" + timeStamp + "_";
        File storageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        if (!storageDirectory.exists()) {
            if (!storageDirectory.mkdirs()) {
                return null;
            }
        }
        File image = File.createTempFile(imageFileName, ".jpg", storageDirectory);
        mImageFileLocation = image.getAbsolutePath();

        return image;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_CAMERA_RESULT:
                mPermissionsGranted = hasAllPermissions(this, PERMISSIONS);
                break;

            default:
                break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        kl.disableKeyguard();
        openBackgroundThread();
        if (mTextureView.isAvailable()) {
            setupCamera(mTextureView.getWidth(), mTextureView.getHeight(),"1");
            openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }
    @Override
    public void onPause() {
        Log.d(TAG,"======******=======onPause=========************");
        closeCamera();
        closeBackgroundThread();
        super.onPause();
    }
    private void setupCamera(int width, int height, String cameraId) {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            Size largestImageSize = Collections.max(
                    Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                    new Comparator<Size>() {
                        @Override
                        public int compare(Size lhs, Size rhs) {
                            return Long.signum(lhs.getWidth() * lhs.getHeight() -
                                    rhs.getWidth() * rhs.getHeight());
                        }
                    }
            );
            mImageReader = ImageReader.newInstance(largestImageSize.getWidth(),
                    largestImageSize.getHeight(),
                    ImageFormat.JPEG,
                    1);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener,
                    mBackgroundHandler);
            mPreviewSize = getPreferredPreviewSize(map.getOutputSizes(SurfaceTexture.class), width, height);
            mCameraId = cameraId;
            Log.d("CAMERA_ID", String.valueOf(mCameraId));
            //    }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private Size getPreferredPreviewSize(Size[] mapSizes, int width, int height) {
        List<Size> collectorSizes = new ArrayList<>();
        for (Size option : mapSizes) {
            if (width > height) {
                if (option.getWidth() > width &&
                        option.getHeight() > height) {
                    collectorSizes.add(option);
                }
            } else {
                if (option.getWidth() > height &&
                        option.getHeight() > width) {
                    collectorSizes.add(option);
                }
            }
        }
        if (collectorSizes.size() > 0) {
            return Collections.min(collectorSizes, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight());
                }
            });
        }
        return mapSizes[0];

    }

    private void openCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
                //return;
            }
            cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }


    }


    private void closeCamera(){
        try {
            mCameraOpenCloseLock.acquire();
            if(mCameraCaptureSession!=null){
                mCameraCaptureSession.close();
                mCameraCaptureSession=null;
            }
            if (mCameraDevice!=null){
                mCameraDevice.close();
                mCameraDevice=null;

                if(mImageReader!=null){

                    mImageReader.close();
                    mImageReader=null;

                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }
    private void createCameraPreviewSession(){
        try{
            SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(),mPreviewSize.getHeight());
            Surface previewSurface= new Surface(surfaceTexture);
            mPreviewCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewCaptureRequestBuilder.addTarget(previewSurface);
            mPreviewCaptureRequestBuilder.set(CaptureRequest.JPEG_QUALITY, (byte)100);
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface,mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            if(mCameraDevice==null){
                                return;
                            }
                            try {
                                mPreviewCaptureRequest = mPreviewCaptureRequestBuilder.build();
                                mCameraCaptureSession = session;
                                mCameraCaptureSession.setRepeatingRequest(
                                        mPreviewCaptureRequest,
                                        mSessionCaptureCallback,
                                        mBackgroundHandler
                                );

                            }catch (CameraAccessException e){
                                e.printStackTrace();
                            }

                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            showToast("create camera session failed!");
                        }
                    },null);

        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }
    private void showToast(final String text) {
        final Activity activity = AndroidCamera.this;
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
    private void openBackgroundThread(){
        mBackgroundThread=new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    private  void closeBackgroundThread(){

        mBackgroundThread.quitSafely();
        try{

            mBackgroundThread.join();
            mBackgroundThread=null;
            mBackgroundHandler=null;

        }catch (InterruptedException e){
            e.printStackTrace();
        }

    }

    private void lockFocus(){
        try{

            mState=STATE_WAIT_LOCK;
            mPreviewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_START);
            mCameraCaptureSession.capture(mPreviewCaptureRequestBuilder.build(),
                    mSessionCaptureCallback,mBackgroundHandler);

        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    private void unLockFocus(){
        try{

            mState=STATE_PREVIEW;
            mPreviewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
            mCameraCaptureSession.capture(mPreviewCaptureRequestBuilder.build(),
                    mSessionCaptureCallback,mBackgroundHandler);

        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    private void captureStillImage(){
        try {

            CaptureRequest.Builder captureStillBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureStillBuilder.addTarget(mImageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureStillBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // setAutoFlash(captureBuilder);

            // Orientation
            int rotation=0;
            //Front camera
            if(mCameraId.equals("1")) {
                rotation = this.getWindowManager().getDefaultDisplay().getRotation();
                captureStillBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));
            }
            else {
                rotation = this.getWindowManager().getDefaultDisplay().getRotation();
                captureStillBuilder.set(CaptureRequest.JPEG_ORIENTATION,
                        ORIENTATIONS.get(rotation));
            }
            CameraCaptureSession.CaptureCallback captureCallback =
                    new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                            super.onCaptureCompleted(session, request, result);

                            //Toast.makeText(getApplicationContext(),"Image Captured",Toast.LENGTH_SHORT).show();

                            unLockFocus();
                        }
                    };

            mCameraCaptureSession.capture(

                    captureStillBuilder.build(),captureCallback,null
            );

        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation +180) % 360;
    }


}

