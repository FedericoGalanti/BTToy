package com.bttoy.btping;

import android.app.Service;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseSettings;
import android.content.Intent;
import android.os.IBinder;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.BeaconTransmitter;

import java.util.Arrays;

public class BeaconService extends Service {
    private static final String TAG = "BeaconActivity";
    private BeaconTransmitter mBeaconTransmitter = null;
    private Beacon beacon = null;
    protected static final String btpingUUID = "7e6985df-4aa3-4bda-bb8b-9f11bf7077a0";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
        // Not needed: see "onStarCommand"
    }

    @Override
    public void onCreate() {
        mBeaconTransmitter = new BeaconTransmitter(getApplicationContext(), new BeaconParser().setBeaconLayout(BeaconParser.ALTBEACON_LAYOUT));
        mBeaconTransmitter.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED);
        mBeaconTransmitter.setConnectable(false);
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        /*
            Since I don't need an on-demand service (i.e. C/S behavior), I want this service to run
            freely without binding, so can run in the background even when activity gets killed.

            On Start, getting the "minor" from the intent, setting up the beacon (AltBeacon),
            try to start advertisement and sets messages for the MainActivityTest.
         */
        String minor = intent.getStringExtra("beacon_minor");
        assert minor != null;
        beacon = new Beacon.Builder()
                .setId1(btpingUUID)
                .setId2("1")
                .setId3(minor)
                .setManufacturer(0x0000)
                .setTxPower(-59)
                .setDataFields(Arrays.asList(0l))
                .build();
        Toast.makeText(getApplicationContext(), "Beacon: " + beacon.getId2() + " " + beacon.getId3(), Toast.LENGTH_SHORT).show();
        mBeaconTransmitter.startAdvertising(beacon, new AdvertiseCallback() {
            @Override
            public void onStartFailure(int errorCode) {
                /*
                    Gives the error code and self stop, since there is no need to have a service
                    running if it does nothing.
                 */
                tellMain("Debug: advertising non avviato! " + errorCode);
                stopSelf();
            }

            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                tellMain("Debug: advertising avviato! " + beacon.getId2() + " " + beacon.getId3());
            }
        });
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        /*
            Stops advertising and notice main that is going off. This is signal for unlocking
            the switch to try restart the beacon service.
            Note: if service gets killed and restarted, beaconing won't work and will throw an error,
            indicating that a beacon is already in place (errorcode 2), because can detect that the
            previous setup wasn't wiped out.
            Since onDestroy shoud take a couple of seconds and checking up on services is deprecated,
            this was the smartest way I had to prevent this situation.
         */
        mBeaconTransmitter.stopAdvertising();
        mBeaconTransmitter = null;
        tellMain("Info: service going off!");
        super.onDestroy();
    }

    private void tellMain(String msg) {
        /*
            Accessory method that fires communications via Broadcast.
         */
        Intent intent = new Intent("BEACON_SERVICE_UPDATE");
        intent.putExtra("BEACON_UPDATE", msg);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }
}
