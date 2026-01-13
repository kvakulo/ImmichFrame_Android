package com.immichframe.immichframe.sensors;

import android.content.Context;
import android.util.Log;

import com.kitesystems.nix.frame.MotionSensor;

import java.io.File;

public class ActivitySensor {
    private final SensorPoller instance;

    public ActivitySensor(Context context, int activitySensorTimeout) {
        if(new File("/etc/nix.model").exists()) {
            instance = new SensorPoller(new MotionSensor(), activitySensorTimeout);
        } else {
            instance = new SensorPoller(() -> true, activitySensorTimeout);
        }
    }

    public void checkSensors(SensorServiceCallback sensorServiceCallback) {
        try {
            Thread.currentThread().setPriority(10);
            instance.checkIfActivityDetected(sensorServiceCallback);
        } catch (SecurityException e2) {
            Log.e("ImmichFrame", e2.toString());
        }
    }
}