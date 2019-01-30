package com.naevatec.camspoc_research;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException;
import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import javax.jmdns.ServiceTypeListener;

import wseemann.media.FFmpegMediaPlayer;

public class MainActivity extends AppCompatActivity implements ServiceListener, ServiceTypeListener {

    // CONSTANTS -----------------------------------------------------------------------------------

    private static final String TAG = "MainActivity";
    private static final int PICKFILE_RESULT_CODE = 128;
    private static final int ALL_PERMISSIONS_REQUEST = 129;

    public static final String MQTT_SERVER_URI = "tcp://broker.hivemq.com:1883";
    private static final String MQTT_TOPIC = "foo/bar";
    private static final String BJ_SERVICE_TYPE = "_googlecast._tcp.local.";

    public static String chosenFilePath;
    private MqttAndroidClient mMqttClient;
    private JmDNS mJmdns;
    private WifiManager.MulticastLock mMulticastLock;
    private FFmpegMediaPlayer mMediaPlayer;
    private SurfaceHolder mSurfaceHolder;

    // LIFECYCLE -----------------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Connect to the MQTT service
        connectToMqtt();

        Log.i(TAG, "DEVICE IP: " + getIPAddress(true));

        Button mqttPublishButton = findViewById(R.id.mqtt_publish);
        Button bjScanButton = findViewById(R.id.bj_scan_on);
        Button bjStopScanButton = findViewById(R.id.bj_scan_off);
        Button ffmpegTestButton = findViewById(R.id.ffmpeg_test);
        Button filePickerButton = findViewById(R.id.file_picker);

        mqttPublishButton.setOnClickListener(v -> publishMessage("json payload"));
        bjScanButton.setOnClickListener(v -> obtainLockAndScan());
        bjStopScanButton.setOnClickListener(v -> releaseLockBJ());
        filePickerButton.setOnClickListener(v -> pickFile());


        ffmpegTestButton.setOnClickListener(v -> {
            testFFmpeg2();
//            testFFmpeg(ffmpegTestButton);
        });

        // Initialize the FFmpeg
        initFFmpegBinaries(ffmpegTestButton);
    }

    // HELPERS -------------------------------------------------------------------------------------

    private void pickFile(){


    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch(requestCode){
            case ALL_PERMISSIONS_REQUEST:
                Log.i(TAG, "onActivityResult: PICKFILE_RESULT_CODE: " + requestCode);
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    Toast.makeText(this, "Permissions granted, try again.", Toast.LENGTH_LONG).show();
                break;
        }
    }

    //    private void pickFile() {
//        Log.i(TAG, "pickFile: Starting file picker");
//        Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
//        chooseFile.setType("*/*");
//
//        Intent intent = Intent.createChooser(chooseFile, getString(R.string.choose_file));
//        startActivityForResult(intent, PICKFILE_RESULT_CODE);
//    }
//
//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//
//        switch(requestCode){
//            case PICKFILE_RESULT_CODE:
//                Log.i(TAG, "onActivityResult: PICKFILE_RESULT_CODE: " + resultCode);
//                if(resultCode == Activity.RESULT_OK) {
//                    if (data == null || data.getData() == null || data.getData().getPath().isEmpty()) {
//                        Toast.makeText(this, "The file path is not valid.", Toast.LENGTH_LONG).show();
//                        Log.i(TAG, "The file path is not valid.");
//                    } else {
//                        Log.e(TAG, "onActivityResult: " + getPath(this, data.getData()));
//                        chosenFilePath = data.getData().getPath();
//                        Log.i(TAG, "The file path is: " + chosenFilePath);
//                    }
//                }
//                break;
//        }
//    }
//
//    public String getPath(Context context, Uri uri) {
//        if ("content".equalsIgnoreCase(uri.getScheme())) {
//            String[] projection = { "_data" };
//            Cursor cursor;
//
//            try {
//                cursor = context.getContentResolver().query(uri, projection, null, null, null);
//                int column_index = cursor.getColumnIndexOrThrow("_data");
//                if (cursor.moveToFirst()) {
//                    return cursor.getString(column_index);
//                }
//            } catch (Exception e) {
//                // Eat it
//            }
//        }
//        else if ("file".equalsIgnoreCase(uri.getScheme())) {
//            return uri.getPath();
//        }
//
//        return null;
//    }

    // FFMPEG --------------------------------------------------------------------------------------

    /**
     * This method copies the ffmpeg binary to device according to device's architecture.
     * Execute whenever you are starting the application or using FFmpeg for the first time.
     *      Loads/Copies binary to device according to architecture
     *      Updates binary if it is using old FFmpeg version
     *      Provides callbacks through FFmpegLoadBinaryResponseHandler interface
     *
     * @param view Any View present in the Activity so we can show the Snackbar
     */
    private void initFFmpegBinaries(View view) {
        FFmpeg ffmpeg = FFmpeg.getInstance(this);
        try {
            ffmpeg.loadBinary(new LoadBinaryResponseHandler() {
                @Override
                public void onFailure() {
                    Log.e(TAG, "Failed to load binaries");
                }
            });
        } catch (FFmpegNotSupportedException e) {
            Log.e(TAG, "This device doesn't support FFmpeg", e);
            Snackbar.make(view, R.string.ffmpeg_not_supported, Snackbar.LENGTH_LONG).show();
        }
    }

    private void testFFmpeg2(){
        if (checkPermissions()) return;

        Log.i(TAG, "testFFmpeg2: Testing");

        mMediaPlayer = new FFmpegMediaPlayer();
        mMediaPlayer.setOnPreparedListener(mediaPlayer -> {
            mediaPlayer.start();
            Log.i(TAG, "testFFmpeg2: " + mediaPlayer.getDuration());
            Log.i(TAG, "testFFmpeg2: " + mediaPlayer.getVideoWidth());
            Log.i(TAG, "testFFmpeg2: " + mediaPlayer.getVideoHeight());

        });
        mMediaPlayer.setOnErrorListener((mp12, what, extra) -> {
            mp12.release();
            return false;
        });

        SurfaceView sv = findViewById(R.id.video_surface);
        mSurfaceHolder = sv.getHolder();

        try {
            String sauce = "/storage/emulated/0/Download/xvul7.mp4";
//            String sauce = "rtp://192.168.112.255:1111";
            mMediaPlayer.reset();
            mMediaPlayer.setDataSource(sauce);
            mMediaPlayer.setDisplay(mSurfaceHolder);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
            mMediaPlayer.prepareAsync();
        } catch (IllegalArgumentException | SecurityException | IllegalStateException | IOException e) {
            e.printStackTrace();
        }
    }

    private void testFFmpeg(View view) {
        if (checkPermissions()) return;

//        String command = "-y -i " + inputFilepath + " -strict  experimental -vcodec libx264 -preset " +
//                "ultrafast -crf 3 -acodec aac -ar 44100 -q:v 20 -vf " +
//                "mp=eq2=1:1.68:0.3:1.25:1:0.96:1" + ouputFile;
//        String command = "-version";
//        String command = "-y -i /storage/0000-000/DCIM/Camera/20170905_151526.mp4 /storage/0000-000/Download/test.avi";
//            String command = "-y -i /storage/0000-0000/Download/abc.mp4 /storage/0000-0000/def.mp4";
//            String command = "-re -f lavfi -i aevalsrc=sin(400*2*PI*t) -ar 8000 -f mulaw -f rtp rtp://127.0.0.1:1234";
//            String command = "-f lavfi -i aevalsrc=sin(400*2*PI*t) -ar 8000 -f mulaw -f udp://239.0.0.1:1234?ttl=13";
//        String command = "-rtsp_flags listen -f rtsp -i rtsp://localhost:5050/ -vcodec copy -acodec copy \n" +
//                "video.mp4";



        String command = "-re -i -srtp_in_suite AES_CM_128_HMAC_SHA1_80 -srtp_in_params zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz srtp://192.168.1.146:7777";



        FFmpeg ffmpeg = FFmpeg.getInstance(this);
        String[] cmd = command.split(" ");
        try {
            // to execute "ffmpeg -version" command you just need to pass "-version"
            ffmpeg.execute(cmd, new ExecuteBinaryResponseHandler() {
                @Override
                public void onFailure(String message) {
                    Log.e(TAG, "Failed to execute: " + command + " With message: " + message);
                }

                @Override
                public void onSuccess(String message) {
                    Log.i(TAG, "Success executing: " + command + " With message: " + message);
                }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            Log.e(TAG, "FFmpeg is already running", e);
            Snackbar.make(view, "FFmpeg is already running", Snackbar.LENGTH_LONG).show();
        }
    }

    private boolean checkPermissions() {
        if ((ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) &&
                (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {
            return false;
        }

        Toast.makeText(this, "The permissions needed are not granted.", Toast.LENGTH_LONG).show();

        String[] permissions = new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE };
        ActivityCompat.requestPermissions(this, permissions,
                ALL_PERMISSIONS_REQUEST);
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseLockBJ();

        if(mMediaPlayer != null)
            mMediaPlayer.release();
        else
            Log.d(TAG, "onPause: The media player was null");
    }

    // MQTT ----------------------------------------------------------------------------------------

    private void publishMessage(String jsonPayload) {
        try {
            byte[] encodedPayload = jsonPayload.getBytes("UTF-8");
            MqttMessage message = new MqttMessage(encodedPayload);
            mMqttClient.publish(MQTT_TOPIC, message);
            Log.d(TAG, "onSuccess: publishMessage");
        } catch (UnsupportedEncodingException | MqttException e) {
            Log.e(TAG, "onFailure: publishMessage", e);
        }
    }

    private void connectToMqtt() {
        String clientId = MqttClient.generateClientId();
        mMqttClient = new MqttAndroidClient(this.getApplicationContext(), MQTT_SERVER_URI,
                        clientId);

        try {
            IMqttToken token = mMqttClient.connect();
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // We are connected
                    Log.d(TAG, "onSuccess: connectToMqtt");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    // Something went wrong e.g. connection timeout or firewall problems
                    Log.e(TAG, "onFailure: connectToMqtt");
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, "onFailure", e);
        }
    }

    // BONJOUR - JMDNS -----------------------------------------------------------------------------

    private void obtainLockAndScan() {
        Log.d(TAG, "Obtain the multicast lock");

        try {
            // Creating a multicast lock
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            mMulticastLock = wm.createMulticastLock(getClass().getName());
            mMulticastLock.setReferenceCounted(false);

            final InetAddress deviceIpAddress = getIPAddress(true);
            mMulticastLock.acquire();
            mJmdns = JmDNS.create(deviceIpAddress, getHostName("HOSTNAME_DEFAULT_VALUE"));
            mJmdns.addServiceTypeListener(this);
            mJmdns.addServiceListener(BJ_SERVICE_TYPE, this);
        } catch (NullPointerException | IOException e){
            Log.e(TAG, "Failure to get a multicast lock", e);
        }
    }

    private void releaseLockBJ() {
        Log.d(TAG, "Release the multicast lock");

        if(mMulticastLock != null)
            mMulticastLock.release();

        if(mJmdns != null) {
            mJmdns.unregisterAllServices();
            try {
                mJmdns.close();
                mJmdns = null;
            } catch (IOException e) {
                Log.e(TAG, "Failure releasing the multicast lock", e);
            }
        }
    }
    /**
     * Get IP address from first non-localhost interface
     * @param useIPv4 Return ipv4 if true, ipv6 if false
     * @return IP address or null
     */
    public static InetAddress getIPAddress(boolean useIPv4) {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress ip : Collections.list(ni.getInetAddresses())) {
                    if(ip.isLoopbackAddress())
                        continue;

                    boolean isIPv4 = ip.getHostAddress().indexOf(':') < 0;
                    if ((useIPv4 && isIPv4) || (!useIPv4 && !isIPv4))
                        return ip;
                }
            }
        } catch (Exception ignored) { } // Ignore right now, change later on

        Log.e(TAG, "No IP address could be found. IPv4 only: " + useIPv4);
        return null;
    }

    // TODO This needs a better alternative
    public static String getHostName(String defValue) {
        try {
            Method getString = Build.class.getDeclaredMethod("getString", String.class);
            getString.setAccessible(true);
            return getString.invoke(null, "net.hostname").toString();
        } catch (Exception ex) {
            return defValue;
        }
    }

    // ServiceListener interface methods

    @Override
    public void serviceAdded(ServiceEvent event) {
        Log.i(TAG, "serviceAdded");
    }

    @Override
    public void serviceRemoved(ServiceEvent event) {
        Log.i(TAG, "serviceRemoved");
    }

    @Override
    public void serviceResolved(ServiceEvent event) {
        ServiceInfo info = event.getInfo();

        Log.i(TAG, "serviceResolved + \n" + info.getApplication() + "\n" +
                info.getQualifiedName() + "\n" + info.getName() + "\n" + info.getNiceTextString() );
    }

    // ServiceTypeListener interface method
    @Override
    public void serviceTypeAdded(ServiceEvent event) {
        Log.i(TAG, "serviceTypeAdded");
    }

    @Override
    public void subTypeForServiceTypeAdded(ServiceEvent event) {
        Log.i(TAG, "subTypeForServiceTypeAdded");
    }

}
