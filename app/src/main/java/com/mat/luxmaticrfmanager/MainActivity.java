package com.mat.luxmaticrfmanager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;


import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity{
    private final String TAG = MainActivity.class.getSimpleName();
    private ScrollView scroll;
    private ProgressBar progressBar;
    private ProgressDialog proDialog;
    private TextView srHour,  ssHour, nowHour;
    private EditText loraAdd, loraApp, loraNet, editRep;

    //스피너
    private Spinner editDimg, srDimming, ssDimming, re1Dimming, re2Dimming, n1Dimming, n2Dimming, re1Hour, re1Min, n1Hour, n1Min, n2Hour, n2Min, re2Hour, re2Min,
                    setSrmin, setSsmin, sMonth, eMonth, sDate, eDate;

    private CheckBox srEnable, ssEnable, re1Enable, re2Enable, n1Enable, n2Enable;
    private Button disconnectBtn, readBtn, writeBtn, dimgBtn, deviceInfoBtn, loraBtn, addWriteBtn, appWriteBtn, netWriteBtn, repBtn, onBtn, offBtn;
    private LinearLayout loraLayout;

    //EXTRA_DATA를 받는 배열
    private int[] readData;
    private String[] loraData;

    private BleService bleService;
    private BluetoothAdapter bleAdapter;
    private BluetoothGattService gattService;
    private ArrayList<BluetoothDevice> devices = new ArrayList<>();
    private BluetoothDevice selectDevice;
    private Handler handler;
    private boolean mScanning = false;
    private boolean mConnect = false;
    private boolean infoChk = false;
    private boolean loraChk = false;
    private static int REQUEST_ACCESS_FINE_LOCATION = 1;
    private static final long SCAN_PERIOD = 10000;
    private String selectAdd;
    private int temp = 0;
    private int writeTemp = 0;
    private int[] deviceInfo;

    private ArrayAdapter<String> bleListAdapter;
    private List list = new ArrayList();

    //비콘 세팅
    private final ParcelUuid BEACON_UUID =
            ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB");
    private final byte FRAME_TYPE_URL = 0x10;
    //비콘 광고를 담당하는 객체 생성
    private BluetoothLeAdvertiser adv;

    private final long FINISH_INTERVAL_TIME = 2000;
    private long backPressedTime = 0;

    private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback(){
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
            //블루투스 스캔 콜백 정의
            boolean dataAdd = true;

            //rssi 값 99이하 중복 리스트 중복 방지 list는 비교를 위한 임시 객체
            if(list.contains(device.getAddress()) && (rssi * -1) < 100){
                dataAdd = false;
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        for(int i = 0; i < bleListAdapter.getCount(); i++){
                            String[] addr = bleListAdapter.getItem(i).split("\n");
                            if(addr[1].equals(device.getAddress())){
                                bleListAdapter.remove(bleListAdapter.getItem(i));
                                //insert로 해당 위치에 삽입(rssi 값이 너무 빨리 변하므로 방지하고자)
                                bleListAdapter.insert(device.getName() + "\n"  + device.getAddress() + "\n" + "RSSI : " + rssi, i);
                            }
                        }
                        //ArrayAdapter 정렬 메서드
                        bleListAdapter.sort(new Comparator<String>() {
                            @Override
                            public int compare(String o1, String o2) {
                                return o1.substring(o1.indexOf("RSSI : ")+8).compareTo(o2.substring(o2.indexOf("RSSI : ")+8));
                            }
                        });
                    }
                }, 2000);

            }else{
                //rssi 99이하, "MAT" 로 시작되는 리스트만 검색
                if(device.getName()!= null && device.getName().contains("MAT") && (rssi * -1) < 100)
                    list.add(device.getAddress());
                else
                    dataAdd = false;
            }
            if(dataAdd) {
                //실제 스레드를 돌리는 로직 위에 조건 만족시 스캔된 디바이스 목록을 추가
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                            devices.add(device);
                            bleListAdapter.add(device.getName() + "\n"  + device.getAddress() + "\n" + "RSSI : " + rssi);
                            bleListAdapter.notifyDataSetChanged();
                    }
                });
            }
        }
    };

    //BleService.java 서비스와 연결 되었을시
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            bleService = ((BleService.LocalBinder) service).getService();
            if(!bleService.initialize()){
                Log.e("TAG","Unable to initialize Bluetooth");
                finish();
            }
            if(!mConnect){
                CreateListDiaLog();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bleService = null;
        }
    };

    //GATT 리시버 정의
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if(bleService.ACTION_GATT_CONNECTED.equals(action)){
                mConnect = true;
                disconnectBtn.setText("연결 끊기");
            }else if(bleService.ACTION_GATT_DISCONNECTED.equals(action)){
                mConnect = false;
                disconnectBtn.setText("기기 검색");

                if(proDialog != null){
                    proDialog.dismiss();
                }

                if(bleAdapter != null){
                    CreateListDiaLog();
                }else{
                    Toast.makeText(getApplicationContext(), "블루투스를 활성화 시켜주세요", Toast.LENGTH_LONG).show();
                    finish();
                }

            }else if(bleService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)){
                gattService = bleService.getSupportedGattServices();

                if(gattService == null){
                    Toast.makeText(getApplicationContext(), "서비스 연결 요청 실패 어플리케이션을 다시 실행시켜 주세요.", Toast.LENGTH_LONG).show();
                    finish();
                }

                try{
                    getData1(gattService);
                }catch(Exception e){
                    Toast.makeText(getApplicationContext(), "일출 일몰 데이터를 가져오지 못했습니다. 다시 연결해 주세요.", Toast.LENGTH_LONG).show();
                    bleService.disconnect();
                }
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try{
                            getData2(gattService);
                        }catch (Exception e){
                            Toast.makeText(getApplicationContext(), "예약 설정 데이터를 가져오지 못했습니다. 다시 연결해 주세요.", Toast.LENGTH_LONG).show();
                            bleService.disconnect();
                        }
                    }
                }, 300);

                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try{
                            getData3(gattService);
                        }catch(Exception e){
                            Toast.makeText(getApplicationContext(), "심야 설정 데이터를 가져오지 못했습니다. 다시 연결해 주세요.", Toast.LENGTH_LONG).show();
                            bleService.disconnect();
                        }
                    }
                }, 600);

                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try{
                            getData4(gattService);
                        }catch(Exception e){
                            Toast.makeText(getApplicationContext(), "기간 설정 데이터를 가져오지 못했습니다. 다시 연결해 주세요.", Toast.LENGTH_LONG).show();
                            bleService.disconnect();
                        }

                    }
                }, 900);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try{
                            getData5(gattService);
                        }catch(Exception e){
                            Toast.makeText(getApplicationContext(), "디밍 제어 데이터를 가져오지 못했습니다. 다시 연결해 주세요.", Toast.LENGTH_LONG).show();
                            bleService.disconnect();
                        }

                    }
                }, 1200);

               /*handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try{
                            getData7(gattService);
                        }catch(Exception e){
                            Toast.makeText(getApplicationContext(), "Lora Add 데이터를 가져오지 못했습니다. 다시 연결해 주세요.", Toast.LENGTH_LONG).show();
                            bleService.disconnect();
                        }

                    }
                }, 1500);*/
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try{
                            getData8(gattService);
                        }catch(Exception e){
                            Toast.makeText(getApplicationContext(), "Lora App key 데이터를 가져오지 못했습니다. 다시 연결해 주세요.", Toast.LENGTH_LONG).show();
                            bleService.disconnect();
                        }

                    }
                }, 1500);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try{
                            getData9(gattService);
                        }catch(Exception e){
                            Toast.makeText(getApplicationContext(), "Lora Net 데이터를 가져오지 못했습니다. 다시 연결해 주세요.", Toast.LENGTH_LONG).show();
                            bleService.disconnect();
                        }

                    }
                }, 1800);

                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try{
                            getData10(gattService);
                        }catch(Exception e){
                            Toast.makeText(getApplicationContext(), "데이터를 가져오지 못했습니다. 다시 연결해 주세요.", Toast.LENGTH_LONG).show();
                            bleService.disconnect();
                        }

                    }
                }, 2100);
                Log.v(TAG, "GATT GET DATA");
            }else if(bleService.ACTION_DATA_AVAILABLE.equals(action)){

                readData = intent.getIntArrayExtra(BleService.EXTRA_DATA);

                if(readData != null && !infoChk){
                    try{
                        if(temp == 0){
                            setView1(readData);
                        }else if(temp == 1){
                            setView2(readData);
                        }else if(temp == 2){
                            setView3(readData);
                        }else if(temp == 3){
                            setView4(readData);
                        }else if(temp == 4){
                            setView5(readData);
                        }/*else if(temp == 5){
                            setView7(readData);
                        }*/else if(temp == 5){
                            setView8(readData);
                        }else if(temp == 6){
                            setView9(readData);
                        }else if(temp == 7){
                            setView10(readData);
                        }
                    }catch (Exception e){
                        e.printStackTrace();
                        Toast.makeText(getApplicationContext(),"데이터 읽기에 실패 하였습니다. 다시 시도해 주세요", Toast.LENGTH_LONG).show();
                    }
                }

                if(infoChk){
                    setView6(readData);
                }

                if(loraChk) {
                    System.out.println("조건까진 오니?");
                    setView7(readData);
                }

                Log.v(TAG, "GATT DATA READ");
            }else if(bleService.ACTION_DATA_WRITE.equals(action)){
                Log.i(TAG, "GATT DATA WRITE");
                if(writeTemp == 0){
                    Toast.makeText(getApplicationContext(), "변경 되었습니다.", Toast.LENGTH_LONG).show();
                }
                writeChk(writeTemp);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //View 초기화
        viewSetting();
        //lora setting layout 초기 hide
        loraLayout.setVisibility(View.GONE);
        //블루투스 권한, 위치 권한 설정
        handler = new Handler();
        final BluetoothManager bleManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bleAdapter = bleManager.getAdapter();

        if(bleAdapter == null){
            Toast.makeText(this, "블루투스 기능을 지원하지 않는 디바이스 입니다.", Toast.LENGTH_LONG).show();
            finish();
        }else if(!bleAdapter.isEnabled()){
            Toast.makeText(getApplicationContext(), "블루투스 기능을 활성화 해주세요", Toast.LENGTH_LONG).show();
            finish();
        }

        //리스트 어댑터 초기화
        bleListAdapter = new ArrayAdapter<>(this, android.R.layout.select_dialog_singlechoice);

        int permissionChk = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);

        if(permissionChk == PackageManager.PERMISSION_DENIED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_ACCESS_FINE_LOCATION);
        }else{
            //BleService 바인딩
            Intent gattServiceIntent = new Intent(MainActivity.this, BleService.class);
            bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        }

    }

    //권한 요청후 액션
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(grantResults[0] == -1){
            Toast.makeText(getApplicationContext(), "위치 권한을 켜야 블루투스 장치를 스캔 할 수 있습니다.", Toast.LENGTH_LONG).show();
            finish();
        }else{
            //BleService 바인딩
            Intent gattServiceIntent = new Intent(MainActivity.this, BleService.class);
            bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if(bleService != null){
            final boolean result = bleService.connect(selectAdd);
            Log.d("TAG","Connect request result =" + result);
        }

        loraBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!mConnect){
                    Toast.makeText(getApplicationContext(), "장비를 연결해 주세요.", Toast.LENGTH_LONG).show();
                }else{
                    if(loraLayout.getVisibility() == View.VISIBLE){
                        loraLayout.setVisibility(View.GONE);
                    }else{
                        loraChk =true;

                        try{
                            getData7(gattService);
                        }catch (Exception e) {
                            Toast.makeText(getApplicationContext(), "Lora Add 데이터를 가져오지 못했습니다. 다시 연결해 주세요.", Toast.LENGTH_LONG).show();
                            bleService.disconnect();
                        }

                        loraLayout.setVisibility(View.VISIBLE);
                    }
                }
            }
        });

        deviceInfoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                infoChk = true;

                if(!mConnect){
                    Toast.makeText(getApplicationContext(), "장비를 연결해 주세요.", Toast.LENGTH_LONG).show();
                }else{

                    try{
                        getData6(gattService);
                    }catch(Exception e){
                        Toast.makeText(getApplicationContext(), "기기 정보 데이터를 가져오지 못했습니다. 다시 연결해 주세요.", Toast.LENGTH_LONG).show();
                        bleService.disconnect();
                    }

                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            String devInfomsg = "";
                            devInfomsg += "----------------------------\n";
                            devInfomsg += "DEV ADDR : " + loraData[0] + " " + loraData[1] + " " + loraData[2] + " " + loraData[3] + "\n";
                            devInfomsg += "DEV EUI : " + loraData[4] + " " + loraData[5] + " " + loraData[6] + " " + loraData[7] + " " + loraData[8] + " " + loraData[9] + " " + loraData[10] + " " + loraData[11] + "\n";
                            devInfomsg += "----------------------------\n";

                            if(deviceInfo[0] == 0){
                                devInfomsg += "통신상태 : NO\n";
                            }else{
                                devInfomsg += "통신상태 : OK\n";
                            }

                            String[] hexData = hexCating(deviceInfo);

                            devInfomsg += "위도 : " + deviceInfo[1] + "." + Integer.decode("0x" + hexData[2] + hexData[3] + hexData[4]) + " 경도 : " + deviceInfo[5] + "." + Integer.decode("0x" + hexData[6] + hexData[7] + hexData[8]) + "\n";
                            devInfomsg += "순시 전력 : " + Integer.decode("0x" + hexData[9] + hexData[10]) +"W\n";
                            devInfomsg += "누적 전력 : " + Integer.decode("0x" + hexData[11] + hexData[12] + hexData[13] + hexData[14]) + "Wh\n";
                            devInfomsg += "전압 : " + Integer.decode("0x" + hexData[15] + hexData[16]) + "V\n";
                            devInfomsg += "전류 : " + Integer.decode("0x" + hexData[17] + hexData[18]) + "mA\n";
                            devInfomsg += "역률: " + Integer.decode("0x" + hexData[19]) + "%\n";
                            devInfomsg += "상태\n";
                            devInfomsg += "----------------------------\n";

                            String [] arrAlarmName = {"누전 알람", "전력량 알람", "과전류 알람", "GPS 알람", "LORA 알람", "미터링 알람", "NOT USED", "정전 알람"};

                            for(int chk = 0; chk < arrAlarmName.length; chk++){
                                int comp = 1 << chk;
                                if((deviceInfo[20] & comp) == comp){
                                    devInfomsg += arrAlarmName[chk] + "\n";
                                }
                            }

                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("기기 정보")
                                    .setMessage(devInfomsg)
                                    .setNeutralButton("확인", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {

                                        }
                                    })
                                    .show();
                        }
                    }, 500);
                }
            }
        });

        disconnectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mConnect){
                    bleService.disconnect();
                }else{
                    CreateListDiaLog();
                }
            }
        });

        writeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(!mConnect){
                    Toast.makeText(getApplicationContext(), "장비를 연결해 주세요.", Toast.LENGTH_LONG).show();
                }else{
                    final byte[] data1 = new byte[10];
                    final byte[] data2 = new byte[8];
                    final byte[] data3 = new byte[8];
                    final byte[] data4 = new byte[4];

                    // 일출 일몰 데이터 가져오기
                    if(srEnable.isChecked()){
                        data1[0] = 1;
                    }else{
                        data1[0] = 0;
                    }
                    if(ssEnable.isChecked()){
                        data1[5] = 1;
                    }else{
                        data1[5] = 0;
                    }
                    data1[1] = (byte) Integer.parseInt((String) setSrmin.getSelectedItem());
                    data1[2] = (byte) Integer.parseInt((String)srDimming.getSelectedItem());
                    data1[3] = (byte) Integer.parseInt(srHour.getText().toString().substring(0, srHour.getText().toString().indexOf("시")));
                    data1[4] = (byte) Integer.parseInt(srHour.getText().toString().substring(srHour.getText().toString().indexOf("시 ") + 2, srHour.getText().toString().indexOf("분")));
                    data1[6] = (byte) Integer.parseInt((String) setSsmin.getSelectedItem());
                    data1[7] = (byte) Integer.parseInt((String) ssDimming.getSelectedItem());
                    data1[8] = (byte) Integer.parseInt(ssHour.getText().toString().substring(0, ssHour.getText().toString().indexOf("시")));
                    data1[9] = (byte) Integer.parseInt(ssHour.getText().toString().substring(ssHour.getText().toString().indexOf("시 ") + 2, ssHour.getText().toString().indexOf("분")));

                    //예약 설정 데이터 가져오기
                    if(re1Enable.isChecked()){
                        data2[0] = 1;
                    }else{
                        data2[0] = 0;
                    }
                    if(re2Enable.isChecked()){
                        data2[4] = 1;
                    }else {
                        data2[4] = 0;
                    }
                    data2[1] = (byte) re1Hour.getSelectedItemPosition();
                    data2[2] = (byte) re1Min.getSelectedItemPosition();
                    data2[3] = (byte) Integer.parseInt((String) re1Dimming.getSelectedItem());
                    data2[5] = (byte) re2Hour.getSelectedItemPosition();
                    data2[6] = (byte) re2Min.getSelectedItemPosition();
                    data2[7] = (byte) Integer.parseInt((String) re2Dimming.getSelectedItem());

                    //심야 설정 데이터 가져오기
                    if(n1Enable.isChecked()){
                        data3[0] = 1;
                    }else{
                        data3[0] = 0;
                    }
                    if(n2Enable.isChecked()){
                        data3[4] = 1;
                    }else{
                        data3[4] = 0;
                    }

                    data3[1] = (byte) n1Hour.getSelectedItemPosition();
                    data3[2] = (byte) n1Min.getSelectedItemPosition();
                    data3[3] = (byte) Integer.parseInt((String) n1Dimming.getSelectedItem());
                    data3[5] = (byte) n2Hour.getSelectedItemPosition();
                    data3[6] = (byte) n2Min.getSelectedItemPosition();
                    data3[7] = (byte) Integer.parseInt((String) n2Dimming.getSelectedItem());

                    //기간 설정 데이터 가져오기
                    data4[0] = (byte) Integer.parseInt((String) sMonth.getSelectedItem());
                    data4[1] = (byte) Integer.parseInt((String) sDate.getSelectedItem());
                    data4[2] = (byte) Integer.parseInt((String) eMonth.getSelectedItem());
                    data4[3] = (byte) Integer.parseInt((String) eDate.getSelectedItem());

                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            final BluetoothGattCharacteristic uuidData1 = gattService.getCharacteristic(UUID.fromString("e1423072-9b07-11ea-bb37-0242ac130002"));
                            uuidData1.setValue(data1);
                            bleService.writeCharacteristic(uuidData1);
                            writeTemp++;
                        }
                    }, 100);

                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            final BluetoothGattCharacteristic uuidData2 = gattService.getCharacteristic(UUID.fromString("e142331a-9b07-11ea-bb37-0242ac130002"));
                            uuidData2.setValue(data2);
                            bleService.writeCharacteristic(uuidData2);
                            writeTemp++;
                        }
                    }, 300);

                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            final BluetoothGattCharacteristic uuidData3 = gattService.getCharacteristic(UUID.fromString("e14235ae-9b07-11ea-bb37-0242ac130002"));
                            uuidData3.setValue(data3);
                            bleService.writeCharacteristic(uuidData3);
                            writeTemp++;
                        }
                    }, 600);

                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            final BluetoothGattCharacteristic uuidData4 = gattService.getCharacteristic(UUID.fromString("e14236b2-9b07-11ea-bb37-0242ac130002"));
                            uuidData4.setValue(data4);
                            bleService.writeCharacteristic(uuidData4);
                            writeTemp++;
                        }
                    }, 900);

                    proDialog = new ProgressDialog(MainActivity.this);
                    proDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                    proDialog.setMessage("설정 중입니다.");
                    proDialog.show();
                }
            }
        });

        dimgBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!mConnect){
                    Toast.makeText(getApplicationContext(), "장비를 연결해 주세요.", Toast.LENGTH_LONG).show();
                }else{

                    byte[] data = new byte[1];
                    data[0] = (byte) Integer.parseInt((String)editDimg.getSelectedItem());
                    final BluetoothGattCharacteristic uuidData5 = gattService.getCharacteristic(UUID.fromString("e1422d8e-9b07-11ea-bb37-0242ac130002"));
                    uuidData5.setValue(data);
                    bleService.writeCharacteristic(uuidData5);

                }
            }
        });

        onBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!mConnect){
                    Toast.makeText(getApplicationContext(), "장비를 연결해 주세요.", Toast.LENGTH_LONG).show();
                }else{
                    byte[] data = {100};
                    final BluetoothGattCharacteristic uuidData5 = gattService.getCharacteristic(UUID.fromString("e1422d8e-9b07-11ea-bb37-0242ac130002"));
                    uuidData5.setValue(data);
                    bleService.writeCharacteristic(uuidData5);
                }
            }
        });

        offBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!mConnect){
                    Toast.makeText(getApplicationContext(), "장비를 연결해 주세요.", Toast.LENGTH_LONG).show();
                }else{
                    byte[] data = {0};
                    final BluetoothGattCharacteristic uuidData5 = gattService.getCharacteristic(UUID.fromString("e1422d8e-9b07-11ea-bb37-0242ac130002"));
                    uuidData5.setValue(data);
                    bleService.writeCharacteristic(uuidData5);
                }
            }
        });

        repBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!mConnect){
                    Toast.makeText(getApplicationContext(), "장비를 연결해 주세요.", Toast.LENGTH_LONG).show();
                }else{
                    byte[] data = new byte[1];
                    data[0] = (byte) Integer.parseInt(editRep.getText().toString());
                    System.out.println(data[0]);
                    final BluetoothGattCharacteristic repData = gattService.getCharacteristic(UUID.fromString("e1423cf2-9b07-11ea-bb37-0242ac130002"));
                    repData.setValue(data);
                    bleService.writeCharacteristic(repData);
                }
            }
        });

        readBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!mConnect){
                    Toast.makeText(getApplicationContext(), "장비를 연결해 주세요", Toast.LENGTH_LONG).show();
                }else{
                    Toast.makeText(getApplicationContext(), "연결된 장비의 설정 정보를 가져옵니다", Toast.LENGTH_LONG).show();

                    gattService = bleService.getSupportedGattServices();
                    proDialog.show();

                    if(gattService == null){
                        Toast.makeText(getApplicationContext(), "서비스 연결 요청 실패 어플리케이션을 다시 실행시켜 주세요", Toast.LENGTH_LONG).show();
                        finish();
                    }

                    try{
                        getData1(gattService);
                    }catch(Exception e){
                        Toast.makeText(getApplicationContext(), "일출 일몰 데이터를 가져오지 못했습니다. 다시 연결해 주세요.", Toast.LENGTH_LONG).show();
                        bleService.disconnect();
                    }
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try{
                                getData2(gattService);
                            }catch (Exception e){
                                Toast.makeText(getApplicationContext(), "예약 설정 데이터를 가져오지 못했습니다. 다시 연결해 주세요.", Toast.LENGTH_LONG).show();
                                bleService.disconnect();
                            }
                        }
                    }, 300);

                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try{
                                getData3(gattService);
                            }catch(Exception e){
                                Toast.makeText(getApplicationContext(), "심야 설정 데이터를 가져오지 못했습니다. 다시 연결해 주세요.", Toast.LENGTH_LONG).show();
                                bleService.disconnect();
                            }
                        }
                    }, 600);

                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try{
                                getData4(gattService);
                            }catch(Exception e){
                                Toast.makeText(getApplicationContext(), "기간 설정 데이터를 가져오지 못했습니다. 다시 연결해 주세요.", Toast.LENGTH_LONG).show();
                                bleService.disconnect();
                            }

                        }
                    }, 900);
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try{
                                getData5(gattService);
                            }catch(Exception e){
                                Toast.makeText(getApplicationContext(), "디밍 제어 데이터를 가져오지 못했습니다. 다시 연결해 주세요.", Toast.LENGTH_LONG).show();
                                bleService.disconnect();
                            }

                        }
                    }, 1200);

                    /*handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try{
                                getData7(gattService);
                            }catch(Exception e){
                                Toast.makeText(getApplicationContext(), "Lora Add 데이터를 가져오지 못했습니다. 다시 연결해 주세요.", Toast.LENGTH_LONG).show();
                                bleService.disconnect();
                            }

                        }
                    }, 1500);*/
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try{
                                getData8(gattService);
                            }catch(Exception e){
                                Toast.makeText(getApplicationContext(), "Lora App key 데이터를 가져오지 못했습니다. 다시 연결해 주세요.", Toast.LENGTH_LONG).show();
                                bleService.disconnect();
                            }

                        }
                    }, 1500);
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try{
                                getData9(gattService);
                            }catch(Exception e){
                                Toast.makeText(getApplicationContext(), "Lora Net 데이터를 가져오지 못했습니다. 다시 연결해 주세요.", Toast.LENGTH_LONG).show();
                                bleService.disconnect();
                            }

                        }
                    }, 1800);

                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try{
                                getData10(gattService);
                            }catch(Exception e){
                                Toast.makeText(getApplicationContext(), "데이터를 가져오지 못했습니다. 다시 연결해 주세요.", Toast.LENGTH_LONG).show();
                                bleService.disconnect();
                            }

                        }
                    }, 2100);

                }
            }
        });

        addWriteBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!mConnect){
                    Toast.makeText(getApplicationContext(), "장비를 연결해 주세요.", Toast.LENGTH_LONG).show();
                }

                byte[] addData = new byte[4];

                if(loraAdd.getText().toString().length() > 8 || loraAdd.getText().toString().length() < 8){
                    Toast.makeText(getApplicationContext(), "잘못된 값입니다 다시 확인해 주세요", Toast.LENGTH_LONG).show();
                }else{
                    addData[0] = (byte) (Integer.decode("0x" + loraAdd.getText().toString().substring(0,2)) & 0xff);
                    addData[1] = (byte) (Integer.decode("0x" + loraAdd.getText().toString().substring(2,4)) & 0xff);
                    addData[2] = (byte) (Integer.decode("0x" + loraAdd.getText().toString().substring(4,6)) & 0xff);
                    addData[3] = (byte) (Integer.decode("0x" + loraAdd.getText().toString().substring(6)) & 0xff);

                    final BluetoothGattCharacteristic loraAddChar = gattService.getCharacteristic(UUID.fromString("e1423860-9b07-11ea-bb37-0242ac130002"));
                    loraAddChar.setValue(addData);
                    bleService.writeCharacteristic(loraAddChar);

                }
            }
        });

        appWriteBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!mConnect){
                    Toast.makeText(getApplicationContext(), "장비를 연결해 주세요.", Toast.LENGTH_LONG).show();
                }

                byte[] appData = new byte[16];
                if(loraApp.getText().toString().length() > 32 || loraApp.getText().toString().length() < 32){
                    Toast.makeText(getApplicationContext(), "잘못된 값입니다 다시 확인해 주세요", Toast.LENGTH_LONG).show();
                }else{
                    appData[0] = (byte) (Integer.decode("0x" + loraApp.getText().toString().substring(0, 2)) & 0xff);
                    appData[1] = (byte) (Integer.decode("0x" + loraApp.getText().toString().substring(2, 4)) & 0xff);
                    appData[2] = (byte) (Integer.decode("0x" + loraApp.getText().toString().substring(4, 6)) & 0xff);
                    appData[3] = (byte) (Integer.decode("0x" + loraApp.getText().toString().substring(6, 8)) & 0xff);
                    appData[4] = (byte) (Integer.decode("0x" + loraApp.getText().toString().substring(8, 10)) & 0xff);
                    appData[5] = (byte) (Integer.decode("0x" + loraApp.getText().toString().substring(10, 12)) & 0xff);
                    appData[6] = (byte) (Integer.decode("0x" + loraApp.getText().toString().substring(12, 14)) & 0xff);
                    appData[7] = (byte) (Integer.decode("0x" + loraApp.getText().toString().substring(14, 16)) & 0xff);
                    appData[8] = (byte) (Integer.decode("0x" + loraApp.getText().toString().substring(16, 18)) & 0xff);
                    appData[9] = (byte) (Integer.decode("0x" + loraApp.getText().toString().substring(18, 20)) & 0xff);
                    appData[10] = (byte) (Integer.decode("0x" + loraApp.getText().toString().substring(20, 22)) & 0xff);
                    appData[11] = (byte) (Integer.decode("0x" + loraApp.getText().toString().substring(22, 24)) & 0xff);
                    appData[12] = (byte) (Integer.decode("0x" + loraApp.getText().toString().substring(24, 26)) & 0xff);
                    appData[13] = (byte) (Integer.decode("0x" + loraApp.getText().toString().substring(26, 28)) & 0xff);
                    appData[14] = (byte) (Integer.decode("0x" + loraApp.getText().toString().substring(28, 30)) & 0xff);
                    appData[15] = (byte) (Integer.decode("0x" + loraApp.getText().toString().substring(30)) & 0xff);

                    final BluetoothGattCharacteristic loraAppChar = gattService.getCharacteristic(UUID.fromString("e142393c-9b07-11ea-bb37-0242ac130002"));
                    loraAppChar.setValue(appData);
                    bleService.writeCharacteristic(loraAppChar);
                }
            }
        });

        netWriteBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!mConnect){
                    Toast.makeText(getApplicationContext(), "장비를 연결해 주세요.", Toast.LENGTH_LONG).show();
                    finish();
                }

                byte[] netData = new byte[16];
                if(loraNet.getText().toString().length() > 32 || loraNet.getText().toString().length() < 32){
                    Toast.makeText(getApplicationContext(), "잘못된 값입니다 다시 확인해 주세요", Toast.LENGTH_LONG).show();
                }else{
                    netData[0] = (byte) (Integer.decode("0x" + loraNet.getText().toString().substring(0, 2)) & 0xff);
                    netData[1] = (byte) (Integer.decode("0x" + loraNet.getText().toString().substring(2, 4)) & 0xff);
                    netData[2] = (byte) (Integer.decode("0x" + loraNet.getText().toString().substring(4, 6)) & 0xff);
                    netData[3] = (byte) (Integer.decode("0x" + loraNet.getText().toString().substring(6, 8)) & 0xff);
                    netData[4] = (byte) (Integer.decode("0x" + loraNet.getText().toString().substring(8, 10)) & 0xff);
                    netData[5] = (byte) (Integer.decode("0x" + loraNet.getText().toString().substring(10, 12)) & 0xff);
                    netData[6] = (byte) (Integer.decode("0x" + loraNet.getText().toString().substring(12, 14)) & 0xff);
                    netData[7] = (byte) (Integer.decode("0x" + loraNet.getText().toString().substring(14, 16)) & 0xff);
                    netData[8] = (byte) (Integer.decode("0x" + loraNet.getText().toString().substring(16, 18)) & 0xff);
                    netData[9] = (byte) (Integer.decode("0x" + loraNet.getText().toString().substring(18, 20)) & 0xff);
                    netData[10] = (byte) (Integer.decode("0x" + loraNet.getText().toString().substring(20, 22)) & 0xff);
                    netData[11] = (byte) (Integer.decode("0x" + loraNet.getText().toString().substring(22, 24)) & 0xff);
                    netData[12] = (byte) (Integer.decode("0x" + loraNet.getText().toString().substring(24, 26)) & 0xff);
                    netData[13] = (byte) (Integer.decode("0x" + loraNet.getText().toString().substring(26, 28)) & 0xff);
                    netData[14] = (byte) (Integer.decode("0x" + loraNet.getText().toString().substring(28, 30)) & 0xff);
                    netData[15] = (byte) (Integer.decode("0x" + loraNet.getText().toString().substring(30)) & 0xff);

                    final BluetoothGattCharacteristic loraNetChar = gattService.getCharacteristic(UUID.fromString("e1423c20-9b07-11ea-bb37-0242ac130002"));
                    loraNetChar.setValue(netData);
                    bleService.writeCharacteristic(loraNetChar);
                }
            }
        });
    }

    @Override
    public void onBackPressed() {
        long tempTime = System.currentTimeMillis();
        long intervalTime = tempTime - backPressedTime;

        if(intervalTime >= 0 && FINISH_INTERVAL_TIME >= intervalTime){
            super.onBackPressed();
        }else{
            backPressedTime = tempTime;
            Toast.makeText(getApplicationContext(), "한번 더 누르면 종료합니다.", Toast.LENGTH_LONG).show();
        }
    }

    private void CreateListDiaLog(){
        String url = "MAS";
        AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build();
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            os.write(new byte[]{FRAME_TYPE_URL, (byte) -16});
            os.write(0x0);
            os.write(url.toString().getBytes());

            AdvertiseData advertiseData = new AdvertiseData.Builder()
                    .addServiceData(BEACON_UUID, os.toByteArray())
                    .addServiceUuid(BEACON_UUID)
                    .setIncludeTxPowerLevel(false)
                    .setIncludeDeviceName(false)
                    .build();

            adv = bleAdapter.getBluetoothLeAdvertiser();
            adv.startAdvertising(advertiseSettings, advertiseData, advertiseCallback());

        }catch(Exception e){
            System.out.println(e);
        }

        if(!mScanning){
            scanLeDevice(true);
        }else {
            scanLeDevice(false);
        }

        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("기기 검색");
        alert.setAdapter(bleListAdapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                if(mScanning){
                    bleAdapter.stopLeScan(leScanCallback);
                    mScanning = false;
                }

                String[] data = bleListAdapter.getItem(which).split("\n");
                selectAdd = data[1];

                proDialog = new ProgressDialog(MainActivity.this);
                proDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                proDialog.setMessage("기기 연결 중..");
                proDialog.show();
                selectDevice = bleAdapter.getRemoteDevice(selectAdd);
                bleService.connect(selectAdd);

            }
        });
        alert.show();
    }

    private void scanLeDevice(final boolean enable){
        temp = 0;
        bleListAdapter.clear();
        devices.clear();
        list.clear();
        if(enable){
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    bleAdapter.stopLeScan(leScanCallback);
                }
            }, SCAN_PERIOD);
            mScanning = true;
            bleAdapter.startLeScan(leScanCallback);
        }else{
            mScanning = false;
            bleAdapter.stopLeScan(leScanCallback);
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BleService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BleService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BleService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BleService.ACTION_DATA_WRITE);
        return intentFilter;
    }



    @Override
    protected void onStop() {
        super.onStop();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mGattUpdateReceiver);
        unbindService(mServiceConnection);
        bleService = null;
    }

    //일출일몰
    private void getData1(BluetoothGattService gattService)throws Exception{
        BluetoothGattCharacteristic data = gattService.getCharacteristic(UUID.fromString("e1423072-9b07-11ea-bb37-0242ac130002"));
        if(data != null){
            bleService.readCharacteristic(data);
        }else{
            Toast.makeText(getApplicationContext(), "일출 일몰 데이터를 읽지 못하였습니다", Toast.LENGTH_LONG).show();
        }
    }

    //예약설정
    private void getData2(BluetoothGattService gattService)throws Exception{
        BluetoothGattCharacteristic data = gattService.getCharacteristic(UUID.fromString("e142331a-9b07-11ea-bb37-0242ac130002"));
        if(data != null){
            bleService.readCharacteristic(data);
        }else{
            Toast.makeText(getApplicationContext(), "예약 설정 데이터를 읽지 못하였습니다", Toast.LENGTH_LONG).show();
        }
    }

    //심야설정
    private void getData3(BluetoothGattService gattService)throws Exception{
        BluetoothGattCharacteristic data = gattService.getCharacteristic(UUID.fromString("e14235ae-9b07-11ea-bb37-0242ac130002"));
        if(data != null){
            bleService.readCharacteristic(data);
        }else{
            Toast.makeText(getApplicationContext(), "심야 설정 데이터를 읽지 못하였습니다", Toast.LENGTH_LONG).show();
        }
    }

    //기간설정
    private void getData4(BluetoothGattService gattService)throws Exception{
        BluetoothGattCharacteristic data = gattService.getCharacteristic(UUID.fromString("e14236b2-9b07-11ea-bb37-0242ac130002"));
        if(data != null){
            bleService.readCharacteristic(data);
        }else{
            Toast.makeText(getApplicationContext(), "기간 설정 데이터를 읽지 못하였습니다", Toast.LENGTH_LONG).show();
        }
    }

    //디밍제어
    private void getData5(BluetoothGattService gattService)throws Exception{
        BluetoothGattCharacteristic data = gattService.getCharacteristic(UUID.fromString("e1422d8e-9b07-11ea-bb37-0242ac130002"));
        if(data != null){
            bleService.readCharacteristic(data);
        }else{
            Toast.makeText(getApplicationContext(), "디밍 데이터를 읽지 못하였습니다", Toast.LENGTH_LONG).show();
        }
    }
    //기기상태
    private void getData6(BluetoothGattService gattService)throws Exception{
        BluetoothGattCharacteristic data = gattService.getCharacteristic(UUID.fromString("e142377a-9b07-11ea-bb37-0242ac130002"));
        if(data != null){
            bleService.readCharacteristic(data);
        }else{
            Toast.makeText(getApplicationContext(), "기기 상태 데이터를 읽지 못하였습니다", Toast.LENGTH_LONG).show();
        }
    }
    //Lora addresss
    private void getData7(BluetoothGattService gattService)throws Exception{
        BluetoothGattCharacteristic data = gattService.getCharacteristic(UUID.fromString("e1423860-9b07-11ea-bb37-0242ac130002"));
        if(data != null){
            bleService.readCharacteristic(data);
        }else{
            Toast.makeText(getApplicationContext(), "Lora 데이터를 읽지 못하였습니다", Toast.LENGTH_LONG).show();
        }
    }
    //Lora App key
    private void getData8(BluetoothGattService gattService)throws Exception{
        BluetoothGattCharacteristic data = gattService.getCharacteristic(UUID.fromString("e142393c-9b07-11ea-bb37-0242ac130002"));
        if(data != null){
            bleService.readCharacteristic(data);
        }else{
            Toast.makeText(getApplicationContext(), "Lora 데이터를 읽지 못하였습니다", Toast.LENGTH_LONG).show();
        }
    }
    //Lora Net
    private void getData9(BluetoothGattService gattService)throws Exception{
        BluetoothGattCharacteristic data = gattService.getCharacteristic(UUID.fromString("e1423c20-9b07-11ea-bb37-0242ac130002"));
        if(data != null){
            bleService.readCharacteristic(data);
        }else{
            Toast.makeText(getApplicationContext(), "Lora 데이터를 읽지 못하였습니다", Toast.LENGTH_LONG).show();
        }
    }

    //보고주기
    private void getData10(BluetoothGattService gattService)throws Exception{
        BluetoothGattCharacteristic data = gattService.getCharacteristic(UUID.fromString("e1423cf2-9b07-11ea-bb37-0242ac130002"));
        if(data != null){
            bleService.readCharacteristic(data);
        }else{
            Toast.makeText(getApplicationContext(), "보고 주기 데이터를 읽지 못하였습니다", Toast.LENGTH_LONG).show();
        }
    }

    //일출일몰
    private void setView1(int[] readData){

        if(readData[0] == 01){
            srEnable.setChecked(true);
        }else{
            srEnable.setChecked(false);
        }
        if(readData[5] == 01){
            ssEnable.setChecked(true);
        }else{
            ssEnable.setChecked(false);
        }
        byte casting1 = (byte) readData[1];
        byte casting2 = (byte) readData[6];

        for(int i = 0; i < 13; i++){
            if(Integer.parseInt((String) setSrmin.getItemAtPosition(i)) == casting1)
                setSrmin.setSelection(i);
        }
        for(int i = 0; i < 11; i++){
            if(Integer.parseInt((String) srDimming.getItemAtPosition(i)) == readData[2])
                srDimming.setSelection(i);
        }

        String srHourData;
        String srMinData;
        if(readData[3] < 10){
            srHourData = "0" + String.valueOf(readData[3]);
        }else{
            srHourData = String.valueOf(readData[3]);
        }

        if(readData[4] < 10){
            srMinData = "0" + String.valueOf(readData[4]);
        }else{
            srMinData = String.valueOf(readData[4]);
        }

        srHour.setText(srHourData + "시 " + srMinData + "분");
        for(int i = 0; i < 13; i++){
            if(Integer.parseInt((String) setSsmin.getItemAtPosition(i)) == casting2)
                setSsmin.setSelection(i);
        }
        for(int i = 0; i < 11; i++){
            if(Integer.parseInt((String) ssDimming.getItemAtPosition(i)) == readData[7])
                ssDimming.setSelection(i);
        }

        String[] nowHourData = new String[4];
        for(int i = 0; i < nowHourData.length; i++){
            if(readData[i + 8] < 10){
                nowHourData[i] = "0" + String.valueOf(readData[i + 8]);
            }else{
                nowHourData[i] = String.valueOf(readData[i + 8]);
            }
        }

        ssHour.setText(nowHourData[0] + "시 " + nowHourData[1] + "분");
        nowHour.setText(nowHourData[2] + " : " + nowHourData[3]);
        temp++;
    }

    //예약설정
    private void setView2(int[] readData){

        if(readData[0] == 01){
            re1Enable.setChecked(true);
        }else{
            re1Enable.setChecked(false);
        }
        if(readData[4] == 01){
            re2Enable.setChecked(true);
        }else{
            re2Enable.setChecked(false);
        }

        re1Hour.setSelection(readData[1]);
        re1Min.setSelection(readData[2]);
        for(int i = 0; i < 11; i++){
            if(Integer.parseInt((String) re1Dimming.getItemAtPosition(i)) == readData[3])
                re1Dimming.setSelection(i);
        }
        re2Hour.setSelection(readData[5]);
        re2Min.setSelection(readData[6]);
        for(int i = 0; i < 11; i++){
            if(Integer.parseInt((String) re2Dimming.getItemAtPosition(i)) == readData[7])
                re2Dimming.setSelection(i);
        }
        temp++;
    }

    //심야설정
    private void setView3(int[] readData){

        if(readData[0] == 01){
            n1Enable.setChecked(true);
        }else{
            n1Enable.setChecked(false);
        }
        if(readData[4] == 01){
            n2Enable.setChecked(true);
        }else{
            n2Enable.setChecked(false);
        }

        n1Hour.setSelection(readData[1]);
        n1Min.setSelection(readData[2]);
        for(int i = 0; i < 11; i++){
            if(Integer.parseInt((String) n1Dimming.getItemAtPosition(i)) == readData[3])
                n1Dimming.setSelection(i);
        }
        n2Hour.setSelection(readData[5]);
        n2Min.setSelection(readData[6]);
        for(int i = 0; i < 11; i++){
            if(Integer.parseInt((String) n2Dimming.getItemAtPosition(i)) == readData[7])
                n2Dimming.setSelection(i);
        }
        temp++;
    }

    //기간설정
    private void setView4(int[] readData){

        for(int i = 0; i < 12; i++){
            if(Integer.parseInt((String) sMonth.getItemAtPosition(i)) == readData[0])
                sMonth.setSelection(i);
        }
        for(int i = 0; i < 31; i++){
            if(Integer.parseInt((String) sDate.getItemAtPosition(i)) == readData[1])
                sDate.setSelection(i);
        }
        for(int i = 0; i < 12; i++){
            if(Integer.parseInt((String) eMonth.getItemAtPosition(i)) == readData[2])
                eMonth.setSelection(i);
        }
        for(int i = 0; i < 31; i++){
            if(Integer.parseInt((String) eDate.getItemAtPosition(i)) == readData[3])
                eDate.setSelection(i);
        }
        temp++;
    }

    //디밍제어
    private void setView5(int[] readData){
        for(int i = 0; i < 11; i++){
            if(Integer.parseInt((String) editDimg.getItemAtPosition(i)) == readData[0])
                editDimg.setSelection(i);
        }
        temp++;
    }

    //기기상태
    private void setView6(int[] readData){
        deviceInfo = readData;
        infoChk = false;
    }

    //Lora address
    private void setView7(int[] readData){
        String viewData = "";
        loraData = hexCating(readData);
        String[] hexData = hexCating(readData);
        for(int i = 0; i < 4; i++){
            viewData += hexData[i];
        }
        loraAdd.setText(String.valueOf(viewData));
//        temp++;
        loraChk = false;
    }

    //Lora App key
    private void setView8(int[] readData){
        String viewData = "";

        String[] hexData = hexCating(readData);
        for(String result : hexData){
            viewData += result;
        }
        loraApp.setText(String.valueOf(viewData));
        temp++;
    }

    //Lora Net
    private void setView9(int[] readData){
        String viewData = "";
        String[] hexData = hexCating(readData);
        for(String result : hexData){
            viewData += result;
        }
        loraNet.setText(String.valueOf(viewData));
        temp++;
    }

    //보고주기
    private void setView10(int[] readData){
        editRep.setText(String.valueOf(readData[0]));
        temp = 0;
        proDialog.dismiss();
    }

    //기본 ui 스케쥴 설정을 수정하였을때
    private void writeChk(int index){
        if(index >= 4){
            proDialog.dismiss();
            Toast.makeText(this, "스케쥴 수정 완료 수정 값 확인은 설정정보확인 버튼을 눌러주세요", Toast.LENGTH_LONG).show();
            writeTemp = 0;
        }
    }
    private void viewSetting(){
        //Layout
        loraLayout = (LinearLayout) findViewById(R.id.loraLayout);
        scroll = (ScrollView) findViewById(R.id.scroll);

        //progress
        progressBar = (ProgressBar) findViewById(R.id.progressBar);

        //TextView
        srHour = (TextView) findViewById(R.id.srHour);
        ssHour = (TextView) findViewById(R.id.ssHour);
        nowHour = (TextView) findViewById(R.id.nowHour);

        //EditText
        loraAdd = (EditText) findViewById(R.id.loraAdd);
        loraApp = (EditText) findViewById(R.id.loraApp);
        loraNet = (EditText) findViewById(R.id.loraNet);
        editRep = (EditText) findViewById(R.id.editRep);

        //Spinner
        editDimg = (Spinner) findViewById(R.id.editDimg);
        srDimming = (Spinner) findViewById(R.id.srDimming);
        ssDimming = (Spinner) findViewById(R.id.ssDimming);
        re1Dimming = (Spinner) findViewById(R.id.re1Dimming);
        re2Dimming = (Spinner) findViewById(R.id.re2Dimming);
        n1Dimming = (Spinner) findViewById(R.id.n1Dimming);
        n2Dimming = (Spinner) findViewById(R.id.n2Dimming);
        re1Hour = (Spinner) findViewById(R.id.re1Hour);
        re1Min = (Spinner) findViewById(R.id.re1Min);
        n1Hour = (Spinner) findViewById(R.id.n1Hour);
        n1Min = (Spinner) findViewById(R.id.n1Min);
        n2Hour = (Spinner) findViewById(R.id.n2Hour);
        n2Min = (Spinner) findViewById(R.id.n2Min);
        re2Hour = (Spinner) findViewById(R.id.re2Hour);
        re2Min = (Spinner) findViewById(R.id.re2Min);
        setSrmin = (Spinner) findViewById(R.id.setSrmin);
        setSsmin = (Spinner) findViewById(R.id.setSsmin);
        sMonth = (Spinner) findViewById(R.id.sMonth);
        eMonth = (Spinner) findViewById(R.id.eMonth);
        sDate = (Spinner) findViewById(R.id.sDate);
        eDate = (Spinner) findViewById(R.id.eDate);

        //CheckBox
        srEnable = (CheckBox) findViewById(R.id.srEnable);
        ssEnable = (CheckBox) findViewById(R.id.ssEnable);
        re1Enable = (CheckBox) findViewById(R.id.re1Enable);
        re2Enable = (CheckBox) findViewById(R.id.re2Enable);
        n1Enable = (CheckBox) findViewById(R.id.n1Enable);
        n2Enable = (CheckBox) findViewById(R.id.n2Enable);

        //Button
        disconnectBtn = (Button) findViewById(R.id.disconnectBtn);
        readBtn = (Button) findViewById(R.id.readBtn);
        writeBtn = (Button) findViewById(R.id.writeBtn);
        dimgBtn = (Button) findViewById(R.id.dimgBtn);
        deviceInfoBtn = (Button) findViewById(R.id.devInfoBtn);
        loraBtn = (Button) findViewById(R.id.loraBtn);
        addWriteBtn = (Button) findViewById(R.id.addWriteBtn);
        appWriteBtn = (Button) findViewById(R.id.appWriteBtn);
        netWriteBtn = (Button) findViewById(R.id.netWriteBtn);
        repBtn = (Button) findViewById(R.id.repBtn);
        onBtn = (Button) findViewById(R.id.onBtn);
        offBtn = (Button) findViewById(R.id.offBtn);
    }

    //16진수 앞0 포함
    private String[] hexCating(int[] data){
        String[] hexData = new String[data.length];
        for(int i = 0; i < data.length; i++){
            if(data[i] < 10){
                hexData[i] = "0" + String.valueOf(data[i]);
            }else if(data[i] == 10){
                hexData[i] = "0a";
            }else if(data[i] == 11){
                hexData[i] = "0b";
            }else if(data[i] == 12){
                hexData[i] = "0c";
            }else if(data[i] == 13){
                hexData[i] = "0d";
            }else if(data[i] == 14){
                hexData[i] = "0e";
            }else if(data[i] == 15){
                hexData[i] = "0f";
            } else{
                hexData[i] = Integer.toHexString(data[i]);
            }
        }
        return hexData;
    }

    //비콘 광고 콜백 함수 정의
    private AdvertiseCallback advertiseCallback(){
        return new AdvertiseCallback() {
            @Override
            public void onStartFailure(int errorCode) {
                super.onStartFailure(errorCode);
            }
        };
    }
}
