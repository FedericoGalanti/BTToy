package com.bttoy.btping;

import android.app.Service;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseSettings;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

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
        super.onCreate();
        mBeaconTransmitter = new BeaconTransmitter(this, new BeaconParser().setBeaconLayout(BeaconParser.ALTBEACON_LAYOUT));
        beacon = new Beacon.Builder()
                .setId1(btpingUUID)
                .setId2("1")
                .setId3("1")
                .setManufacturer(0x0000)
                .setTxPower(-59)
                .setDataFields(Arrays.asList(0l))
                .build();
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
        if (!startAdverisingPings()){
            Log.e(TAG, "Service interrotto: advertising fallito!");
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopAdvertisingPings();
    }

    //Controllo prerequisiti per eseguire il beaconing
    private boolean startAdverisingPings(){
        mBeaconTransmitter.startAdvertising(beacon, new AdvertiseCallback() {
            @Override
            public void onStartFailure(int errorCode) {
                //Log.e(TAG, "Avvio advertisement fallito, codice: "+ errorCode);
            }

            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                //Log.i(TAG, "Advertisement avviato!");
            }
        });
        return mBeaconTransmitter.isStarted();
    }

    private void stopAdvertisingPings(){
        mBeaconTransmitter.stopAdvertising();
    }
}
