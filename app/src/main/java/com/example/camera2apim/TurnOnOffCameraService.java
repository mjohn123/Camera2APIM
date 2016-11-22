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
        new Handler(getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                mCountDownTimer_App = new CountDownTimer(4000, 1000) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                    }
                    @Override
                    public void onFinish() {
                        Intent camera_intent = new Intent(TurnOnOffCameraService.this, AndroidCamera.class);
                        camera_intent.putExtra("AndroidCamera", true);
                        camera_intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        getApplicationContext().startActivity(camera_intent);
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
