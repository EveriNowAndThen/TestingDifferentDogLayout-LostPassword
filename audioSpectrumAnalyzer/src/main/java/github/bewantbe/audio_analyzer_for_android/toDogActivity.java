package github.bewantbe.audio_analyzer_for_android;

import android.content.SharedPreferences;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ListView;
import android.widget.Switch;
import android.Manifest;


import com.wowwee.bluetoothrobotcontrollib.chip.ChipRobot;
import com.wowwee.bluetoothrobotcontrollib.chip.ChipRobotFinder;


import java.util.ArrayList;
import java.util.List;


public class toDogActivity extends Activity implements ChipRobot.ChipRobotInterface {

    static String prefDirect = "MyPrefs";
    static SharedPreferences dogPref;
    static SharedPreferences.Editor dogEdit;
    ChipBaseFragment baseFragmentReady;

    private boolean isFirst = false;
    private static final int REQUEST_ENABLE_BT = 1;
    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;
    private BluetoothAdapter mBluetoothAdapter;
    private TextView textView;
    private ListView listView;
    public Switch attemptAutoBool;
    List<String> robotNameList;
    LocationManager locationManager;
    String provider;
    String dogChangeVar;// Will probably be changed to a class, for now string placeholder

    @Override
    public void onCreate(Bundle savedInstances) {

        super.onCreate(savedInstances);
        setContentView(R.layout.todog);

        final int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

        dogPref = getApplicationContext().getSharedPreferences(prefDirect, 0);
        dogEdit = dogPref.edit();


        //getActivity().getWindow().getDecorView().setSystemUiVisibility(flags);


        //View view = inflater.inflate(R.layout.fragment_connect, container, false);

        textView = (TextView) findViewById(R.id.topTV);
        listView = (ListView) findViewById(R.id.connectionTable);
        attemptAutoBool = (Switch) findViewById(R.id.AttemptAuto);
        String[] robotNameArr = {"Please turn on CHIP"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(toDogActivity.this, android.R.layout.simple_list_item_1, android.R.id.text1, robotNameArr);
        listView.setAdapter(adapter);
        checkLocationPermission();


        final Button refreshBtn = (Button) findViewById(R.id.refreshBtn);
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ChipRobotFinder.getInstance().clearFoundChipList();
                scanLeDevice(false);
                updateChipList();
                scanLeDevice(true);
            }
        });

        final Button listConnectToDog = (Button) findViewById(R.id.toConnectionList);
        listConnectToDog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshConnection();
            }
        });

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        // listConnectToDog.setVisibility(View.INVISIBLE);

        provider = locationManager.getBestProvider(new Criteria(), false);

        this.initBluetooth();


        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position,
                                    long id) {
                if (ChipRobotFinder.getInstance().getChipFoundList().size() > 0) { // If there exists a dog in the list
                    String targetRobotName = robotNameList.get(position); // clicked robot becomes the robot name
                    dogChangeVar = targetRobotName;
                    // Save typical robot ID and submit the check to false.
                    for (ChipRobot robot : (List<ChipRobot>) ChipRobotFinder.getInstance().getChipFoundList()) {
                        if (robot.getName().contentEquals(targetRobotName)) {
                            final ChipRobot connectChipRobot = robot;
                            toDogActivity.this.runOnUiThread(new Runnable() {
                                public void run() {
                                    connect(connectChipRobot);
                                    // Stop scan Chip
                                    scanLeDevice(false);
                                }
                            });
                            dogEdit.putString("dogID", targetRobotName);
                            dogEdit.putBoolean("firstConnect", false);
                            dogEdit.commit();
                            openAnalyzerActivity();
                            break;
                        }
                    }
                }
            }
        });
        // We want to set a listview listener for when a field is added or changed. When that happens, attempt to autoconnect if it
        // is the prefered dog
        if (!dogPref.getBoolean("firstConnect",true)){
            attemptAutoBool.setChecked(true);
            toDogActivity.dogEdit.commit();
        }
        // Conditional to check if this is the first time the program has run. If yes, we ignore it, if not, we just chill and wait for the onclick listeners for connecting
        // Make this just attempt auto correct, have an if otherwise

    }



    void connect(ChipRobot robot) {
        robot.setCallbackInterface(this);
        robot.connect(toDogActivity.this);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (dogPref.getBoolean("firstConnect",true)){
            attemptAutoBool.setChecked(false);
            toDogActivity.dogEdit.commit();
        }

        // Register ChipRobotFinder broadcast receiver
        this.registerReceiver(mChipFinderBroadcastReceiver, ChipRobotFinder.getChipRobotFinderIntentFilter());

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                try {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);

                } catch (ActivityNotFoundException ex) {
                }
            }
        }

        // Start scan
        ChipRobotFinder.getInstance().clearFoundChipList();
        scanLeDevice(false);
        updateChipList();
        scanLeDevice(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        this.unregisterReceiver(mChipFinderBroadcastReceiver);
        scanLeDevice(false);
    }

    private void initBluetooth(){
        final BluetoothManager bluetoothManager = (BluetoothManager) this.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        ChipRobotFinder.getInstance().setBluetoothAdapter(mBluetoothAdapter);
        ChipRobotFinder.getInstance().setApplicationContext(this);
    }

    public void scanLeDevice(final boolean enable) {
        if (enable) {
            Log.d("ChipScan", "Scan Le device start");
            // Stops scanning after a pre-defined scan period.
            ChipRobotFinder.getInstance().scanForChipContinuous();
            if (attemptAutoBool.isChecked()) {
                updateChipList();
            }
        }else{
            Log.d("ChipScan", "Scan Le device stop");
            ChipRobotFinder.getInstance().stopScanForChipContinuous();
        }
    }

    public void updateChipList(){
        robotNameList = new ArrayList();
        for (ChipRobot robot : (List<ChipRobot>)ChipRobotFinder.getInstance().getChipFoundList()) {
            robotNameList.add(robot.getName());
            if (attemptAutoBool.isChecked()) {
                String myString;
                myString = Boolean.toString(attemptAutoBool.isChecked());
                Log.d("Attemping Chip Auto", "Attempting Chip Auto connect");
                final ChipRobot connectChipRobot = robot;
                toDogActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        connect(connectChipRobot);
                        // Stop scan Chip
                        scanLeDevice(false);
                    }
                });
                openAnalyzerActivity();
                break;
                }
            }


        String[] robotNameArr;
        if (robotNameList.size() > 0){
            robotNameArr = robotNameList.toArray(new String[0]);

        }
        else {
            robotNameArr = new String[1];
            robotNameArr[0] = "Please turn on CHIP";
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, android.R.id.text1, robotNameArr);
        listView.setAdapter(adapter);
    }

    private final BroadcastReceiver mChipFinderBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (ChipRobotFinder.ChipRobotFinder_ChipFound.equals(action)){
                BluetoothDevice device = (BluetoothDevice)(intent.getExtras().get("BluetoothDevice"));
                Log.d("BLE", "ChipScanFragment broadcast receiver found Chip: " + device.getName());
                updateChipList();
            } else if (ChipRobotFinder.ChipRobotFinder_ChipListCleared.equals(action)) {
                updateChipList();
            }
        }
    };




    public void openAnalyzerActivity(){
            Intent intent = new Intent(this,AnalyzerActivity.class);
            startActivity(intent);
    }

    @Override
    public void chipDeviceReady(ChipRobot chipRobot) {

    }

    @Override
    public void chipDeviceDisconnected(ChipRobot chipRobot) {

    }

    @Override
    public void chipDidReceiveVolumeLevel(ChipRobot chipRobot, byte b) {

    }

    @Override
    public void chipDidReceivedBatteryLevel(ChipRobot chipRobot, float v, byte b, byte b1) {

    }

    @Override
    public void chipDidReceiveDogVersionWithBodyHardware(int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7, int i8, int i9) {

    }

    @Override
    public void chipDidSendAdultOrKidSpeed() {

    }

    @Override
    public void chipDidReceiveAdultOrKidSpeed(byte b) {

    }

    @Override
    public void chipDidReceiveEyeRGBBrightness(byte b) {

    }

    @Override
    public void chipDidReceiveCurrentClock(int i, int i1, int i2, int i3, int i4, int i5, int i6) {

    }

    @Override
    public void chipDidReceiveCurrentAlarm(int i, int i1, int i2, int i3, int i4) {

    }

    @Override
    public void chipDidReceiveBodyconStatus(int i) {

    }
    public boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);

            return false;
        } else {
            return true;
        }
    }
    public void refreshConnection(){
        dogEdit.putBoolean("firstConnect",true);
        dogEdit.putString("dogID"," ");
        dogEdit.commit();
        ChipRobotFinder.getInstance().clearFoundChipList();
        scanLeDevice(false);
        updateChipList();
        scanLeDevice(true);
    }


    }


