package com.bttoy.btping;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.Region;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;


public class ScanningService extends Service implements BeaconConsumer {
    protected static final String TAG = "ScanningService";
    protected static final Identifier btpingUUID = Identifier.parse("7e6985df-4aa3-4bda-bb8b-9f11bf7077a0");
    private static final String CHANNEL_ID = "001";
    private static final String[] status = {"Outside", "Inside"};
    private NotificationManager mNotification = null;
    /*
        BeaconManager's here because I want an instance of it to be at disposal, since I will
        call frequently "startMonitoring" for each new Beacon. I declare it here and set up in
        onCreate.
     */
    private BeaconManager beaconManager = null;
    /*
        Regions: regions are references of "what am I looking for". I set up a generic region,
        to identify and scan for my UUID and my "Major". Then we have the arrays of regions: that's
        because of how Monitoring works. Regions are roughly equal to the identifiers of a beacon,
        thus are used to set up the monitors for EVERY BEACON.
        The HashMap is a knowledge base of the Beacons that may come in sight or not and is useful
        to retrieve Beacon objects: since Regions are strictly associated to a beacon, are used in
        the monitoring callbacks and beacons need a builder (ton of work to be done), this map is
        used to quickly retrieve beacons by using their region.
     */
    private Region beaconRegion = null;
    private HashMap<Region, Beacon> inSight = new HashMap<>();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNotification = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        //Istanzio il Beacon Manager
        beaconManager = BeaconManager.getInstanceForApplication(getApplicationContext());
        /*
            Il Beacon Manager, per riconoscere i Beacons (AltBeacon, quindi tutti i beacon),
            sfrutta dei parser per fare in modo di "capire" la struttura di qualsiasi protocollo usato,
            che sia "iBeacon", "Eddystone" (tutti e quattro i frames) o altri protocolli.

            In particolare, i <protocol>_LAYOUTS specificano come dovrebbero essere i pacchetti inviati
            da ogni protocollo, in modo da poterli catturare e comprendere. Nello specifico:

            ALTBEACON   "m:2-3=beac,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"

            EDDYSTONE  TLM  "x,s:0-1=feaa,m:2-2=20,d:3-3,d:4-5,d:6-7,d:8-11,d:12-15"

            EDDYSTONE  UID  "s:0-1=feaa,m:2-2=00,p:3-3:-41,i:4-13,i:14-19"

            EDDYSTONE  URL  "s:0-1=feaa,m:2-2=10,p:3-3:-41,i:4-20v"

            IBEACON  "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"

            Dove:
            - Termine 1: ID del Manufacturer
            - Termine "i" (Altbeacon e IBeacon): UUID, Major e Minor
            - Termine "p": potenza del segnale (RSSI, legato al TX)
            - Termine "d": data payload
            Per ora lavoriamo solo con AltBeacon.
         */
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(BeaconParser.ALTBEACON_LAYOUT));
        beaconManager.setEnableScheduledScanJobs(false);
        beaconManager.setBackgroundBetweenScanPeriod(2000);
        beaconManager.setBackgroundScanPeriod(2000);
        //Creating region for ranging
        beaconRegion = new Region("BTPing Region", btpingUUID,
                Identifier.parse("1"), null);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        /*
            Same thing of Beacon Service. This thing must be going on and on.
            Notable thing: setting up is in "onCreate" because we want to setup all BEFORE the
            "bind" call. Because "Bind" starts up the scanning system. In other words:
            .bind() -> onBeaconServiceConnect()
         */
        beaconManager.bind(this);
        tellMain("Info: service on!", null);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        /*
            Cleaning up before the kill. Probably should be setting up an interrupt for the
            monitoringSetup()s threads (see didDetermineStateForRegion).
            Unbind eventually stops the scanning and destroys everything, but I wanted to be sure
            to not leave things unturned by setting up a "bsts" force stop of all the scanning tasks.
         */
        stopScanning();
        beaconManager.unbind(this);
        tellMain("Info: service going off!", null);
        super.onDestroy();
    }

    @Override
    public void onBeaconServiceConnect() {
        /*
            Cleaning up all notifiers before setting up again. I don't need to keep an eye on them,
            since they are just the callbacks for the "action", not for every single beacon scanned.
            Those are for general behavior.
         */
        beaconManager.removeAllMonitorNotifiers();
        beaconManager.removeAllRangeNotifiers();

        beaconManager.addMonitorNotifier(new MonitorNotifier() {
            @Override
            public void didEnterRegion(Region region) {
            }

            @Override
            public void didExitRegion(Region region) {

            }

            @Override
            public void didDetermineStateForRegion(int state, Region region) {
                /*
                    Instead of using didEnter/Exit, this callback gives the "state" of the "beacon"
                    (represented by the region). State = 1 (INSIDE), State = 0 (OUTSIDE).
                    Then tells the main if the beacon entered or exited the region, allowing main to
                    update the listview (check receiver, "Beacon" case).
                    Ulterior logic can be done, but pay attention to not overload it. Juggling with
                    the other two callbacks for better handling of more complex cases, is interesting.
                 */
                Beacon b = inSight.get(region);
                tellMain("Beacon: " + status[state], b);
            }
        });

        beaconManager.addRangeNotifier((collection, region) -> {
            /*
                Ranging is not what it seems: the collection (Beacons) is "every single beacon seen",
                so if a beacon got out of range, collection would still hold information about it.
                I tried to check if it would get a strange value for range, if beacon was to get out
                of range, but it's pointless to work in here, when you can work with MonitorNotifiers.
                Just feeds monitoringSetup() every now and then to allow checking for new beacons
                coming to town.

                Thread x monitoringSetup(): this is done because the notifiers have little to no time
                for heavier execution. With this, I delegate a thread to take care of it in parallel.
                Of course, this is a "naive" solutions, edge cases may lead to disastrous consequences.
                Possible things to take care of:
                - Try/catch the thread "run" for Interrupts, in order to kill it in onDestroy
                - Handling the tuning of the scanning to allow the thread to catch up fast with data
                    changes in case of big numbers of beacons (of course, under possibilities of
                    the device in usage)
                -
             */
            if (collection.size() > 0) {
                ArrayList<Beacon> newSeen = new ArrayList<>(collection);
                new Thread(() -> monitoringSetup(newSeen)).start();
            }
        });

        try {
            beaconManager.startRangingBeaconsInRegion(beaconRegion);
            tellMain("Debug: range started!", null);
        } catch (RemoteException e) {
            tellMain("Debug: Remote exception: " + e.toString(), null);
            e.printStackTrace();
        }
    }

    private void stopScanning() {
        /*
            Manual shutdown for scanning ops.
            Takes down every monitored region and stops the ranging.
            No controls is dued because stopMonitoringBeaconsInRegion/stopRangingBeaconsInRegion
            have no consequences if were already stopped.
         */
        try {
            for (Region r : inSight.keySet()) beaconManager.stopMonitoringBeaconsInRegion(r);
            beaconManager.stopRangingBeaconsInRegion(beaconRegion);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void monitoringSetup(ArrayList<Beacon> list) {
        for (Beacon b : list) {
            Region r = new Region("BTPing Region", btpingUUID, b.getId2(), b.getId3());
            if (!inSight.containsKey(r)) {
                inSight.put(r, b);
                try {
                    beaconManager.startMonitoringBeaconsInRegion(r);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void tellMain(String msg, Beacon b) {
        Intent intent = new Intent("SCANNING_SERVICE_UPDATE");
        // You can also include some extra data.
        intent.putExtra("SCANNING_UPDATE", msg);
        if (b != null) intent.putExtra("BEACON", (Serializable) b);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
        //showNotification(msg);
    }

    private void showNotification(final String msg) {
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Scanning Service")
                .setContentText(msg)
                .setSmallIcon(R.drawable.bt_notification_icon)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        if (mNotification != null) mNotification.notify(0, notification);
    }
}

