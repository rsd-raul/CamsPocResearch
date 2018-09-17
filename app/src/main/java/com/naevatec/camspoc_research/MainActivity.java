package com.naevatec.camspoc_research;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
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

public class MainActivity extends AppCompatActivity implements ServiceListener, ServiceTypeListener {

    // CONSTANTS -----------------------------------------------------------------------------------

    public static final String TAG = "MainActivity";

    public static final String MQTT_SERVER_URI = "tcp://broker.hivemq.com:1883";
    private static final String MQTT_TOPIC = "foo/bar";
    private static final String BJ_SERVICE_TYPE = "_googlecast._tcp.local.";

    private MqttAndroidClient client;
    private JmDNS jmdns;
    private WifiManager.MulticastLock multicastLock;

    // LIFECYCLE -----------------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        connectToMqtt();

        Button mqttPublishButton = findViewById(R.id.mqtt_publish);
        Button bjScanButton = findViewById(R.id.bj_scan_on);
        Button bjStopScanButton = findViewById(R.id.bj_scan_off);

        mqttPublishButton.setOnClickListener(v -> publishMessage("json payload"));
        bjScanButton.setOnClickListener(v -> obtainLockAndScan());
        bjStopScanButton.setOnClickListener(v -> releaseLock());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseLock();
    }

    // MQTT ----------------------------------------------------------------------------------------

    private void publishMessage(String jsonPayload) {
        try {
            byte[] encodedPayload = jsonPayload.getBytes("UTF-8");
            MqttMessage message = new MqttMessage(encodedPayload);
            client.publish(MQTT_TOPIC, message);
            Log.d(TAG, "onSuccess: publishMessage");
        } catch (UnsupportedEncodingException | MqttException e) {
            Log.e(TAG, "onFailure: publishMessage", e);
        }
    }

    private void connectToMqtt() {
        String clientId = MqttClient.generateClientId();
        client = new MqttAndroidClient(this.getApplicationContext(), MQTT_SERVER_URI,
                        clientId);

        try {
            IMqttToken token = client.connect();
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
            multicastLock = wm.createMulticastLock(getClass().getName());
            multicastLock.setReferenceCounted(false);

            final InetAddress deviceIpAddress = getIPAddress(true);
            multicastLock.acquire();
            jmdns = JmDNS.create(deviceIpAddress, getHostName("HOSTNAME_DEFAULT_VALUE"));
            jmdns.addServiceTypeListener(this);
            jmdns.addServiceListener(BJ_SERVICE_TYPE, this);
        } catch (NullPointerException | IOException e){
            Log.e(TAG, "Failure to get a multicast lock", e);
        }
    }

    private void releaseLock() {
        Log.d(TAG, "Release the multicast lock");

        if(multicastLock != null)
            multicastLock.release();

        if(jmdns != null) {
            jmdns.unregisterAllServices();
            try {
                jmdns.close();
                jmdns = null;
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
        } catch (Exception ignored) { } // for now eat exceptions

        Log.e(TAG, "No IP address could be found. IPv4 only: " + useIPv4);
        return null;
    }

    // TODO This need a better alternative
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
                info.getQualifiedName() + "\n" + info.getName() + "\n" + info.getNiceTextString());
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
