package com.example.camera2apim;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;


public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void turn_on_Service(View view) {
        Intent serviceIntent = new Intent(getApplicationContext(), TurnOnOffCameraService.class);
        getApplicationContext().startService(serviceIntent);
        finish();
    }
    public void turn_off_Service(View view) {
        Intent serviceIntent = new Intent(getApplicationContext(), TurnOnOffCameraService.class);
        getApplicationContext().stopService(serviceIntent);
    }
}
