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
        // Non necessito di effettuare il binding. Vedi "OnStart"
    }

    @Override
    public void onCreate() {
        mBeaconTransmitter = new BeaconTransmitter(getApplicationContext(), new BeaconParser().setBeaconLayout(BeaconParser.ALTBEACON_LAYOUT));
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        /*
            Considerando che deve effettuare un'operazione long-running e che controllo il service
            dall'attività con i pulsanti, mi basta farlo avviare. I controlli sul Bluetooth, verranno
            fatti dall'attività, quindi, se il BT dovesse mancare o il dispositivo non fosse abilitato,
            posso controllare il Service per farlo stoppare o per prevenirne la creazione.
         */
        super.onStartCommand(intent, flags, startId);
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
        mBeaconTransmitter.stopAdvertising();
        mBeaconTransmitter = null;
        super.onDestroy();
    }

    private void tellMain(String msg) {
        Intent intent = new Intent("BEACON_SERVICE_UPDATE");
        // You can also include some extra data.
        intent.putExtra("BEACON_UPDATE", msg);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }


}
