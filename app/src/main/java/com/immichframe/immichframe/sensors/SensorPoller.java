package com.immichframe.immichframe.sensors;

import android.util.Log;

public class SensorPoller {
    public static boolean sleepModeActive = false;
    long durationBeforeSleep;
    public long idleDuration = 0;

    private final HardwareSensor hardwareSensor;

    public SensorPoller(HardwareSensor hardwareSensor, int wakeLockMinutes) {
        this.hardwareSensor = hardwareSensor;
        this.durationBeforeSleep = wakeLockMinutes * 60L;
    }

    public void checkIfActivityDetected(SensorServiceCallback callback) {
        if (!hardwareSensor.isActivityDetected()) {
            if (this.idleDuration % 60 == 0) {
                Log.d("SensorPoller", String.format("No activity detected for %d minutes; In sleep mode: %b", this.idleDuration / 60, sleepModeActive));
            }
            this.idleDuration += 1;

            boolean noMotionForFullDuration = this.idleDuration > this.durationBeforeSleep;
            if (noMotionForFullDuration) {
                if(!sleepModeActive) {
                    Log.d("SensorPoller", "-------Going to sleep--------");
                }
                callback.sleep();
                sleepModeActive = true;
                return;
            }
            return;
        }
        this.idleDuration = 0L;
        callback.wakeUp();
    }
}