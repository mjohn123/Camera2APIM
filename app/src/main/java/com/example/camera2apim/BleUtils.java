package com.example.camera2apim;

import android.content.Context;

public class BleUtils {

    private static final String BLE_PREF = "blePrefFile";
    private static final String CAMERA_LENS = "cameraLens";

    public static void setCameraLens(Context context, String name) {
        context.getSharedPreferences(BLE_PREF, Context.MODE_PRIVATE).edit()
                .putString(CAMERA_LENS, name)
                .commit();
    }

    public static String getCameraLens(Context context) {
        String s=context.getSharedPreferences(BLE_PREF, Context.MODE_PRIVATE).getString(CAMERA_LENS, "");
        return s;
    }



}
