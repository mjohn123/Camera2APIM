package com.example.camera2apim;

import android.app.KeyguardManager;
import android.app.Service;
import android.content.Intent;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

public class TurnOnOffCameraService extends Service {

    private CountDownTimer mCountDownTimer_App;
    boolean flag=true;
    private KeyguardManager keyguardManager;
    KeyguardManager.KeyguardLock kl;

    public TurnOnOffCameraService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("Service", "========onCreate in ReadService=============");
        keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        kl = keyguardManager.newKeyguardLock("MyKeyguardLock");
         new Handler(getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                mCountDownTimer_App = new CountDownTimer(1500, 1000) {

                    @Override
                    public void onTick(long millisUntilFinished) {
                        if(flag==true) {
                            kl.disableKeyguard();
                            PowerManager pm = (PowerManager) getSystemService(getApplicationContext().POWER_SERVICE);
                            PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK
                                | PowerManager.ACQUIRE_CAUSES_WAKEUP
                                | PowerManager.ON_AFTER_RELEASE, "MyWakeLock");
                            wakeLock.acquire();
                            flag=false;
                        }
                    }

                    @Override
                    public void onFinish() {
                        // Your stuff
                        EventBus.getDefault().post(new OnCaptureEvent("exit"));
                        Intent camera_intent = new Intent(TurnOnOffCameraService.this, AndroidCamera.class);
                        camera_intent.putExtra("AndroidCamera", true);
                        camera_intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        getApplicationContext().startActivity(camera_intent);
                        mCountDownTimer_App.start();
                        flag=true;
                        kl.reenableKeyguard();
                    }
                };
                mCountDownTimer_App.start();
            }
        });
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
         return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if(mCountDownTimer_App!=null){
            mCountDownTimer_App.cancel();
        }

    }

}
