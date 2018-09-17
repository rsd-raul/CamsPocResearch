package com.naevatec.camspoc_research;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.Formatter;
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
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;
import javax.jmdns.ServiceTypeListener;

public class MainActivity extends AppCompatActivity implements ServiceListener, ServiceTypeListener {

    public static final String TAG = "MainActivity";

    private MqttAndroidClient client;
    private JmDNS jmdns;
    private WifiManager.MulticastLock multicastLock;
    private final String SERVICE_TYPE = "_googlecast._tcp.local.";
    private final String MQTT_TOPIC = "foo/bar";

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

    private void obtainLockAndScan() {
        try {
            // Creating a multicast lock
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            multicastLock = wm.createMulticastLock(getClass().getName());
            multicastLock.setReferenceCounted(false);

            final InetAddress deviceIpAddress = getIPAddress(true);
            multicastLock.acquire();
            jmdns = JmDNS.create(deviceIpAddress, getHostName("HOSTNAME_DEFAULT_VALUE"));
            jmdns.addServiceTypeListener(this);
            jmdns.addServiceListener(SERVICE_TYPE, this);
        } catch (NullPointerException | IOException e){
            Log.e(TAG, "onFailure: 1", e);
        }
    }

    private void releaseLock() {
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
        client = new MqttAndroidClient(this.getApplicationContext(), "tcp://broker.hivemq.com:1883",
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
                    Log.d(TAG, "onFailure: connectToMqtt");
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, "onFailure", e);
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

    // TODO This need a better solution
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
        Log.i(TAG, "serviceResolved");
        Log.i(TAG, event.getInfo().getApplication());
        Log.i(TAG, event.getInfo().getQualifiedName());
        Log.i(TAG, event.getInfo().getName());
        Log.i(TAG, event.getInfo().getNiceTextString());
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
