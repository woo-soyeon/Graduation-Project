package com.example.saycheeze;


import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.annotation.TargetApi;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.Editable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.w3c.dom.Text;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;


public class MainActivity extends AppCompatActivity
        implements CameraBridgeViewBase.CvCameraViewListener2, Watermark_Fragment.OnTimePointSetListener {

    private static final String TAG = "opencv";
    private Mat matInput;
    private Mat matResult;
    int default_x = 0;
    int default_y = 0;
    int count = 0;


    private CameraBridgeViewBase mOpenCvCameraView;

    //public native void ConvertRGBtoGray(long matAddrInput, long matAddrResult);
    public native long loadCascade(String cascadeFileName );
    public native int detect(long cascadeClassifier_face,
                             long cascadeClassifier_eye, long matAddrInput, long matAddrResult);
    public native int detectx(long cascadeClassifier_face,
                              long cascadeClassifier_eye, long matAddrInput, long matAddrResult);


    public long cascadeClassifier_face = 0;
    public long cascadeClassifier_eye = 0;

    /*-------------------------------bluetooth??????------------------------------------------------------------*/
    public static final int SEND = 1;

    BluetoothAdapter mBluetoothAdapter;
    Set<BluetoothDevice> mPairedDevices;
    List<String> mListPairedDevices;

    Handler mBluetoothHandler;
    ConnectedBluetoothThread mThreadConnectedBluetooth;
    BluetoothDevice mBluetoothDevice;
    BluetoothSocket mBluetoothSocket;
    private ListView m_ListView;     //??????????????? ???????????? ?????? ???????????? ?????? ??????.
    private ArrayAdapter<String> m_Adapter;     //????????? ????????? ?????? ????????? ?????? ??????.

    ArrayList<String> values = new ArrayList<>(); //ArrayList??? ???????????????.


    final static int BT_REQUEST_ENABLE = 1;
    final static int BT_MESSAGE_READ = 2;
    final static int BT_CONNECTING_STATUS = 3;
    final static UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    /*-------------------------------bluetooth??????------------------------------------------------------------*/


    /*--------------------??????????????? ?????? ???????????? ??????-------------------------------*/
    private final Semaphore writeLock = new Semaphore(1);

    public void getWriteLock() throws InterruptedException {
        writeLock.acquire();
    }

    public void releaseWriteLock() {
        writeLock.release();
    }
    /*------------------------------------------------------------------------------*/


    static {
        System.loadLibrary("opencv_java4");
        System.loadLibrary("native-lib");
    }
    private void copyFile(String filename) {
        String baseDir = Environment.getExternalStorageDirectory().getPath();
        String pathDir = baseDir + File.separator + filename;

        AssetManager assetManager = this.getAssets();

        InputStream inputStream = null;
        OutputStream outputStream = null;

        try {
            Log.d( TAG, "copyFile :: ?????? ????????? ???????????? "+ pathDir);
            inputStream = assetManager.open(filename);
            outputStream = new FileOutputStream(pathDir);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            inputStream.close();
            inputStream = null;
            outputStream.flush();
            outputStream.close();
            outputStream = null;
        } catch (Exception e) {
            Log.d(TAG, "copyFile :: ?????? ?????? ??? ?????? ?????? "+e.toString() );
        }

    }

    private void read_cascade_file(){
        copyFile("haarcascade_frontalface_alt.xml");
        copyFile("haarcascade_eye_tree_eyeglasses.xml");

        Log.d(TAG, "read_cascade_file:");

        cascadeClassifier_face = loadCascade( "haarcascade_frontalface_alt.xml");
        Log.d(TAG, "read_cascade_file:");

        cascadeClassifier_eye = loadCascade( "haarcascade_eye_tree_eyeglasses.xml");
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);


        mOpenCvCameraView = (CameraBridgeViewBase)findViewById(R.id.activity_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setMaxFrameSize(2960,1440);
        mOpenCvCameraView.setCameraIndex(0); // front-camera(1),  back-camera(0)


        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fm.beginTransaction();
        fragmentTransaction.add(R.id.fragment, new Main_Fragment());
        fragmentTransaction.commit();

        //View view = new MyView(this);
        //setContentView(view);

        /*---------------------- ????????? ?????? ?????? ?????? ????????? ---------------------------------------*/
        ImageButton button = (ImageButton)findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                try {
                    getWriteLock();

                    File path = new File(Environment.getExternalStorageDirectory() + "/Images/");
                    path.mkdirs();
                    File file = new File(path, System.currentTimeMillis() +"image.jpg");

                    String filename = file.toString();

                    Imgproc.cvtColor(matResult, matResult, Imgproc.COLOR_BGR2RGBA);
                    boolean ret  = Imgcodecs.imwrite( filename, matResult);
                    if ( ret ) Log.d(TAG, "SUCESS");
                    else Log.d(TAG, "FAIL");


                    Intent mediaScanIntent = new Intent( Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    mediaScanIntent.setData(Uri.fromFile(file));
                    sendBroadcast(mediaScanIntent);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }


                releaseWriteLock();

            }
        });
        /*-------------------------------------------------------------------------------------------------------*/
        /*-------------------------------bluetooth??????------------------------------------------------------------*/
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        final ToggleButton btnon = (ToggleButton)findViewById(R.id.btnon);
        btnon.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(btnon.isChecked()) {
                    btnon.setBackgroundDrawable(getResources().getDrawable(R.drawable.bluetoothon));
                    bluetoothOn();
                }
                else{
                    btnon.setBackgroundDrawable(getResources().getDrawable(R.drawable.bluetoothoff));
                    bluetoothOff();
                }
            }
        });
        ImageButton btnservice = (ImageButton)findViewById(R.id.btnservice);
        btnservice.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                listPairedDevices();
            }
        });

        //?????? ????????? ??????

        //?????? ????????? ??????

//??????????????? ??????
        mBluetoothHandler = new Handler(){
            public void handleMessage(android.os.Message msg){
                if(msg.what == BT_MESSAGE_READ){
                    String readMessage = null;
                    try {
                        readMessage = new String((byte[]) msg.obj, "UTF-8");

                        values.add(readMessage);
                        m_Adapter.notifyDataSetChanged();
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }



                    if (count >= 5){
                        try {
                            Toast.makeText(MainActivity.this, readMessage, Toast.LENGTH_LONG).show();
                            getWriteLock();

                            File path = new File(Environment.getExternalStorageDirectory() + "/Images/");
                            path.mkdirs();
                            File file = new File(path, System.currentTimeMillis() + "image.jpg");

                            String filename = file.toString();

                            Imgproc.cvtColor(matInput, matInput, Imgproc.COLOR_BGR2RGBA);
                            boolean ret = Imgcodecs.imwrite(filename, matInput);
                            if (ret) Log.d(TAG, "SUCESS");
                            else Log.d(TAG, "FAIL");

                            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                            mediaScanIntent.setData(Uri.fromFile(file));
                            sendBroadcast(mediaScanIntent);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        releaseWriteLock();
                        count = 0;
                    }
                    count ++;
                }
            }
        };

        m_Adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, values);
        //musicList??? ???????????? ?????? ???????????? ????????? ????????? ???????????? ???????????????.
        //m_ListView = findViewById(R.id.resultList);
        //???????????? ??????????????? ??????????????? ???????????????.
        //m_ListView.setAdapter(m_Adapter);
        /*-------------------------------bluetooth??????------------------------------------------------------------*/

        /*-------------------------------information??????----------------------------------------------------------*/
        ImageButton information = (ImageButton)findViewById(R.id.information);
        information.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(MainActivity.this);
                LayoutInflater inflater = getLayoutInflater();
                View view = inflater.inflate(R.layout.dialog_infromation, null);
                ImageView iv = (ImageView)view.findViewById(R.id.iv);
                iv.setImageResource(R.drawable.information);
                //getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                builder.setView(view);
                builder.setNegativeButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        dialog.dismiss();;
                    }
                });

                builder.show();
            }
        });
        /*-------------------------------information??????----------------------------------------------------------*/

    }

    /*@Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        String msg = "????????? ???????????? : "+x+" / "+y;

        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();

        return super.onTouchEvent(event);
    }*/

    /*protected class MyView extends View {
        public  MyView(Context context) {
            super(context);
        }

        public boolean onTouchEvent(MotionEvent event) {

            super.onTouchEvent(event);

            if(event.getAction() == MotionEvent.ACTION_DOWN) {
                float x = event.getX();
                float y = event.getY();

                String msg = "????????? ???????????? : "+x+" / "+y;

                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        }
    }*/

    //-------------------------------------------------------------------------bluetooth----------------------------------------------------------------------------------------------
    void bluetoothOn() {
        if(mBluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "??????????????? ???????????? ?????? ???????????????.", Toast.LENGTH_LONG).show();
        }
        else {
            if (mBluetoothAdapter.isEnabled()) {
                Toast.makeText(getApplicationContext(), "??????????????? ?????? ????????? ?????? ????????????.", Toast.LENGTH_LONG).show();

            }
            else {
                Toast.makeText(getApplicationContext(), "??????????????? ????????? ?????? ?????? ????????????.", Toast.LENGTH_LONG).show();
                Intent intentBluetoothEnable = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(intentBluetoothEnable, BT_REQUEST_ENABLE);
            }
        }
    }

    void bluetoothOff() {
        if (mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.disable();
            Toast.makeText(getApplicationContext(), "??????????????? ???????????? ???????????????.", Toast.LENGTH_SHORT).show();

        }
        else {
            Toast.makeText(getApplicationContext(), "??????????????? ?????? ???????????? ?????? ????????????.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case BT_REQUEST_ENABLE:
                if (resultCode == RESULT_OK) { // ???????????? ???????????? ????????? ??????????????????
                    Toast.makeText(getApplicationContext(), "???????????? ?????????", Toast.LENGTH_LONG).show();

                } else if (resultCode == RESULT_CANCELED) { // ???????????? ???????????? ????????? ??????????????????
                    Toast.makeText(getApplicationContext(), "??????", Toast.LENGTH_LONG).show();

                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


    void listPairedDevices() {
        if (mBluetoothAdapter.isEnabled()) {
            mPairedDevices = mBluetoothAdapter.getBondedDevices();

            if (mPairedDevices.size() > 0) {
                androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
                builder.setTitle("?????? ??????");

                mListPairedDevices = new ArrayList<String>();
                for (BluetoothDevice device : mPairedDevices) {
                    mListPairedDevices.add(device.getName());
                    //mListPairedDevices.add(device.getName() + "\n" + device.getAddress());
                }
                final CharSequence[] items = mListPairedDevices.toArray(new CharSequence[mListPairedDevices.size()]);
                mListPairedDevices.toArray(new CharSequence[mListPairedDevices.size()]);

                builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int item) {
                        connectSelectedDevice(items[item].toString());
                    }
                });

                AlertDialog alert = builder.create();
                alert.show();
            } else {
                Toast.makeText(getApplicationContext(), "???????????? ????????? ????????????.", Toast.LENGTH_LONG).show();
            }
        }
        else {
            Toast.makeText(getApplicationContext(), "??????????????? ???????????? ?????? ????????????.", Toast.LENGTH_SHORT).show();
        }
    }


    void connectSelectedDevice(String selectedDeviceName) {
        for(BluetoothDevice tempDevice : mPairedDevices) {
            if (selectedDeviceName.equals(tempDevice.getName())) {
                mBluetoothDevice = tempDevice;
                break;
            }
        }
        try {
            mBluetoothSocket = mBluetoothDevice.createRfcommSocketToServiceRecord(BT_UUID);
            mBluetoothSocket.connect();
            mThreadConnectedBluetooth = new ConnectedBluetoothThread(mBluetoothSocket);
            mThreadConnectedBluetooth.start();
            mBluetoothHandler.obtainMessage(BT_CONNECTING_STATUS, 1, -1).sendToTarget();
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "???????????? ?????? ??? ????????? ??????????????????.", Toast.LENGTH_LONG).show();
        }
    }

    private class ConnectedBluetoothThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedBluetoothThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "?????? ?????? ??? ????????? ??????????????????.", Toast.LENGTH_LONG).show();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }
        public void run() {

            byte[] buffer = new byte[1024];
            int bytes;

            while (true) {
                try {
                    bytes = mmInStream.available();
                    if (bytes != 0) {
                        //????????? ????????????
                        SystemClock.sleep(100);
                        bytes = mmInStream.available();
                        bytes = mmInStream.read(buffer, 0, bytes);
                        mBluetoothHandler.obtainMessage(BT_MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                    }
                } catch (IOException e) {
                    break;
                }
            }
        }

        public void writedata(String str) {
            byte[] bytes = str.getBytes();
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "????????? ?????? ??? ????????? ??????????????????.", Toast.LENGTH_LONG).show();
            }
        }
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "?????? ?????? ??? ????????? ??????????????????.", Toast.LENGTH_LONG).show();
            }
        }

    }

    //-------------------------------------------------------------------------bluetooth----------------------------------------------------------------------------------------------
    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "onResume :: Internal OpenCV library not found.");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "onResum :: OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }


    public void onDestroy() {
        super.onDestroy();

        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    //????????? ?????? ?????? ???????????? ?????????
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        /*????????? ??????*/
        try {
            getWriteLock();
            /*--------------------------------------*/
            /*-----????????????-----*/

            matInput = inputFrame.rgba();

            if ( matResult == null )

                matResult = new Mat(matInput.rows(), matInput.cols(), matInput.type());

            //ConvertRGBtoGray(matInput.getNativeObjAddr(), matResult.getNativeObjAddr());
            //Core.flip(matInput, matInput, 1);

            int facex = detect(cascadeClassifier_face,cascadeClassifier_eye, matInput.getNativeObjAddr(),
                    matResult.getNativeObjAddr());
            int facey = detectx(cascadeClassifier_face,cascadeClassifier_eye, matInput.getNativeObjAddr(),
                    matResult.getNativeObjAddr());

            //Log.d(TAG, "face " + facex + " " + facey + " found");



            if(mThreadConnectedBluetooth != null) {

                mThreadConnectedBluetooth.writedata(default_x + " " + default_y + " " + facex + " " + facey);
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        releaseWriteLock();
        /*---------------*/

        //Watermark_Fragment fragment = (Watermark_Fragment)getFragmentManager().findFragmentById(R.id.fragment);
        //fragment.imageViewLocation();

        return matResult;
    }

    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(mOpenCvCameraView);
    }


    //??????????????? ????????? ?????? ?????????
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 200;


    protected void onCameraPermissionGranted() {
        List<? extends CameraBridgeViewBase> cameraViews = getCameraViewList();
        if (cameraViews == null) {
            return;
        }
        for (CameraBridgeViewBase cameraBridgeViewBase: cameraViews) {
            if (cameraBridgeViewBase != null) {
                cameraBridgeViewBase.setCameraPermissionGranted();

                read_cascade_file();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        boolean havePermission = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(CAMERA) != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{CAMERA, WRITE_EXTERNAL_STORAGE}, CAMERA_PERMISSION_REQUEST_CODE);
                havePermission = false;
            }
        }
        if (havePermission) {
            onCameraPermissionGranted();
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.M)
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            onCameraPermissionGranted();
        }else{
            showDialogForPermission("?????? ??????????????? ???????????? ????????????????????????.");
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


    @TargetApi(Build.VERSION_CODES.M)
    private void showDialogForPermission(String msg) {

        AlertDialog.Builder builder = new AlertDialog.Builder( MainActivity.this);
        builder.setTitle("??????");
        builder.setMessage(msg);
        builder.setCancelable(false);
        builder.setPositiveButton("???", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id){
                requestPermissions(new String[]{CAMERA, WRITE_EXTERNAL_STORAGE}, CAMERA_PERMISSION_REQUEST_CODE);
            }
        });
        builder.setNegativeButton("?????????", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface arg0, int arg1) {
                finish();
            }
        });
        builder.create().show();
    }

    @Override
    public void OnTimePointSet(int[] imageViewLocation){
        default_x = imageViewLocation[0];
        default_y = imageViewLocation[1];
    }

}