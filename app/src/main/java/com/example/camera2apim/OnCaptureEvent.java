package com.example.camera2apim;

/**
 * Created by Toan Duc Bui on 8/21/2016.
 */
public class OnCaptureEvent {
    private String codeCapture;

    public OnCaptureEvent(String code){
        this.codeCapture = code;
    }

    public String getCodeCapture(){
        return codeCapture;
    }
}