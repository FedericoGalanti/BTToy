package com.bttoy.btping;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.Region;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;


public class ScanningServiceTest extends Service implements BeaconConsumer {
    protected static final String TAG = "ScanningServiceTest";
    protected static final Identifier btpingUUID = Identifier.parse("7e6985df-4aa3-4bda-bb8b-9f11bf7077a0");
    private static final String CHANNEL_ID = "001";
    /*
        BeaconManager's here because I want an instance of it to be at disposal, for ease to make calls.
        I declare it here and set up in onCreate.
     */
    private BeaconManager beaconManager = null;
    /*
        Regions: regions are references of "what am I looking for". I set up a generic region,
        to identify and scan for my UUIDs (like the "domain" for my app).
        I keep two lists: one that lists beacons, that will be passed to the activity for re-building
        the listview, one that lists out-of-range lost beacons, to be purged from the listview and
        to report to the user via Notification.
     */
    private Region beaconRegion = null;
    private ArrayList<Beacon> listBeacons = new ArrayList<>();
    private ArrayList<Beacon> lostBeacons = new ArrayList<>();
    //[TESTING]
    private ArrayList<String> loggingExitRegion = new ArrayList<>();
    private ArrayList<String> loggingListUpdate = new ArrayList<>();
    private int i, j = 0;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //Beacon Manager setup
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
        //Creating region for ranging and setting Scan periods
        beaconManager.setBackgroundScanPeriod(5000);
        beaconManager.setBackgroundBetweenScanPeriod(5 * 60 * 1000);
        beaconManager.setForegroundScanPeriod(2000);
        beaconManager.setForegroundBetweenScanPeriod(10000);
        try {
            beaconManager.updateScanPeriods();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
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
        try {
            produceLog();
        } catch (IOException e) {
            e.printStackTrace();
        }
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
                /*
                    This method will be called when a Beacon gets out of the region.
                    It's a crude implementation of a reactive behavior: check if there are lost
                    beacons since last scans, send them to activity for list updating and warns
                    via Notification users about them going out-of-range.
                    Thread is necessary because this callbacks can handle only very light work.
                 */
                new Thread(() -> {
                    //[TESTING = Test 1 (velocità scanner) + Test 2 (velocità ]
                    long start = System.nanoTime();
                    if (!lostBeacons.isEmpty()) {
                        tellMain("Beacon: outside", lostBeacons);
                        StringBuilder sb = new StringBuilder();
                        for (Beacon b : lostBeacons) {
                            String beaconString = " " + b.getId3().toString() + "\n";
                            sb.append(beaconString);
                        }
                        showNotification("Scanning warning!", "Beacon(s)\n" + sb.toString() + "\nOut of range!");
                    }
                    lostBeacons.clear();
                    loggingExitRegion.add("Scan thread (Exit Region) " + i + ": exec time " + ((System.nanoTime() - start) / 10000) + " ms\n");
                    i++;
                }).start();
            }

            @Override
            public void didDetermineStateForRegion(int state, Region region) {

            }
        });

        beaconManager.addRangeNotifier((collection, region) -> {
            /*
                This will check periodically the beacons in sight. Since it's called periodically,
                it isn't reactive, so the Monitors are used to make the scanning more reactive, even
                if not in a very smart way. From this, I get the list of in-sight beacons to update
                the listview and compute the missing beacons for further update of the original list,
                or for new behaviors (like saving beacons that went missing somewhere in another list,
                perhaps).

                Thread x monitoringSetup(): again, this is done because the notifiers have little to no time
                for heavier execution. With this, I delegate a thread to take care of it in parallel.
                Again, of course, this is a "naive" solutions, edge cases may lead to possible problems.
                Possible things to take care of:
                - Try/catch the thread "run" for Interrupts, in order to kill it in onDestroy
                - Handling the tuning of the scanning to allow the thread to catch up fast with data
                    changes in case of big numbers of beacons (of course, under possibilities of
                    the device in usage)
             */
            if (collection.size() > 0) {
                ArrayList<Beacon> newSeen = new ArrayList<>(collection);
                new Thread(() -> {
                    long start = System.nanoTime();
                    listBeaconUpdate(newSeen);
                    loggingListUpdate.add("Scan thread (Exit Region) " + j + ": exec time " + ((System.nanoTime() - start) / 10000) + " ms\n");
                    j++;
                }).start();
            }
        });

        try {
            beaconManager.startMonitoringBeaconsInRegion(beaconRegion);
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
            No controls are dued because stopMonitoringBeaconsInRegion/stopRangingBeaconsInRegion
            have no consequences if were already stopped.
         */
        try {
            beaconManager.stopMonitoringBeaconsInRegion(beaconRegion);
            beaconManager.stopRangingBeaconsInRegion(beaconRegion);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private synchronized void listBeaconUpdate(ArrayList<Beacon> list) {
        /*
            Functions to compute the missing beacons and to notify the activity of the new list
            of beacons in-sight.
            listBeacons is used as a pivot for calculations.
         */
        lostBeacons = listBeacons;
        lostBeacons.removeAll(list);
        tellMain("Beacon: Inside", list);
        listBeacons.clear();
        listBeacons.addAll(list);
    }


    private void tellMain(String msg, ArrayList<Beacon> b) {
        Intent intent = new Intent("SCANNING_SERVICE_UPDATE");
        intent.putExtra("SCANNING_UPDATE", msg);
        if (b != null) intent.putParcelableArrayListExtra("BEACON", b);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    private synchronized void showNotification(final String msg, final String subtext) {
        NotificationManager mNotification = getSystemService(NotificationManager.class);
        NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, "Scan_Notify", NotificationManager.IMPORTANCE_DEFAULT);
        assert mNotification != null;
        mNotification.createNotificationChannel(mChannel);
        Intent intent = new Intent(getApplicationContext(), MainActivityTest.class);
        PendingIntent pi = PendingIntent.getActivity(getBaseContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.bt_notification_icon)
                .setContentTitle("Scanning update")
                .setContentText(msg)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(subtext))
                .setContentIntent(pi);
        mNotification.notify(1, notification.build());
    }

    //[TESTING]
    private void writeLog(File file) throws IOException {
        BufferedWriter buf = new BufferedWriter(new FileWriter(file));
        SimpleDateFormat stf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ITALY);
        buf.append("START OF LOG SESSION: ").append(stf.format(new Date())).append("\n\n");
        buf.append("LOGGING: Test for Scanning Service Exit Region ---\n");
        //2. Append Exit Region Logs
        for (String s : loggingExitRegion) {
            buf.append(s);
        }
        buf.append("LOGGING: Test for Scanning Service List Update ---\n");
        //3. Append Ranging Region Logs
        for (String s : loggingListUpdate) {
            buf.append(s);
        }
        //4. Close file and flush
        buf.flush();
        buf.close();
    }

    public void produceLog() throws IOException {
        //1. Open File
        //Path: Scheda di memoria > Android > data > com.bbtoy > logfile.txt
        File logfile;
        if (Environment.getExternalStorageState() == null) {
            logfile = new File(this.getDataDir(), "/logfileScanner.txt");
        } else {
            logfile = new File(this.getExternalFilesDir(null), "/logfileScanner.txt");
        }
        if (logfile.exists()) writeLog(logfile);
        else {
            if (logfile.createNewFile()) writeLog(logfile);
        }
    }


}

