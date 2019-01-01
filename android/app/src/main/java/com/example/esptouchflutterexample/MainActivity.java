package com.example.esptouchflutterexample;

import android.Manifest;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import io.flutter.app.FlutterActivity;
import io.flutter.plugins.GeneratedPluginRegistrant;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.content.pm.PackageManager;

import com.espressif.iot.esptouch.EsptouchTask;
import com.espressif.iot.esptouch.IEsptouchResult;
import com.espressif.iot.esptouch.IEsptouchTask;
import com.espressif.iot.esptouch.util.ByteUtil;
import com.espressif.iot.esptouch.util.EspNetUtil;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

public class MainActivity extends FlutterActivity {
  private static final String CHANNEL = "samples.flutter.io/esptouch";
  private static final String TAG = "MainActivity";

  private IEsptouchTask mEsptouchTask;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    GeneratedPluginRegistrant.registerWith(this);

    new MethodChannel(getFlutterView(), CHANNEL).setMethodCallHandler(
        new MethodCallHandler() {
            @Override
            public void onMethodCall(MethodCall call, Result result) {
                if (call.method.equals("startSmartConfig")) {
                    String ssid = call.argument("ssid");
                    String bssid = call.argument("bssid");
                    String pass = call.argument("pass");
                    String deviceCount = call.argument("deviceCount");
                    String broadcast = call.argument("broadcast");

                    startSmartConfig(ssid, bssid, pass, deviceCount, broadcast, result);
                }
                else if (call.method.equals("stopSmartConfig")) {
                     stopSmartConfig();
                }
                else if (call.method.equals("getConnectedWiFiInfo")) {
                    getWifiInfo(result);
                }
                else if (call.method.equals("getBatteryLevel")) {
                    int batteryLevel = getBatteryLevel();

                    if (batteryLevel != -1) {
                        result.success(batteryLevel);
                    } else {
                        result.error("UNAVAILABLE", "Battery level not available.", null);
                    }
                } else {
                    result.notImplemented();
                }
            }});
  }

  private void getWifiInfo(Result result) {
      final ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
      final NetworkInfo networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

      if (networkInfo != null && networkInfo.isConnected()) {
          final WifiManager wifiManager = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
          final WifiInfo wifiInfo = wifiManager.getConnectionInfo();

          if(wifiInfo != null) {
              final String ssid = wifiInfo.getSSID().replaceAll("^\"|\"$", "");
              final String bssid = wifiInfo.getBSSID();
              String is5G = "unknow";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                  int frequency = wifiInfo.getFrequency();
                  if (frequency > 4900 && frequency < 5900) {
                      // Connected 5G wifi. Device does not support 5G
                      is5G = "yes";
                  } else {
                      is5G = "no";
                  }
            }

            JSONObject re = new JSONObject();
            try {
                re.put("ssid", ssid);
                re.put("bssid", bssid);
                re.put("is5G", is5G);
            } catch (JSONException ex) {
                result.error("getWifiInfo", ex.getMessage(), null);
                return;
            }

            result.success(re.toString());
          } else {
              result.error("getWifiInfo", "Unable to obtain WiFi details", null);
          }
      } else {
          result.error("getWifiInfo", "Not connected to WiFi", null);
      }
  }


  public void stopSmartConfig() {
        if (mEsptouchTask != null) {
            mEsptouchTask.interrupt();
        }
    }

    private void startSmartConfig(String ssid, String bssid, String pass, String deviceCount, String broadcast,  final Result resultResp) {
      stopSmartConfig();

        /*Log.d(TAG, "ssid " + ssid);
        Log.d(TAG, "bssid " + bssid);
        Log.d(TAG, "pass " + pass);
        Log.d(TAG, "deviceCount " + deviceCount);
        Log.d(TAG, "broadcast " + broadcast);
        */

        byte[] apSsid =  ByteUtil.getBytesByString(ssid);
        byte[] apBssid = EspNetUtil.parseBssid2bytes(bssid);
        byte[] apPassword = ByteUtil.getBytesByString(pass);
        byte[] deviceCountData = deviceCount.getBytes();
        byte[] broadcastData = broadcast.getBytes();

      new EsptouchAsyncTask4(new TaskListener() {
          @Override
          public void onFinished(List<IEsptouchResult> result) {
              // Do Something after the task has finished

              

              try {
                IEsptouchResult firstResult = result.get(0);

                if (!firstResult.isCancelled()) {
                    if (firstResult.isSuc()) {
                        StringBuilder sb = new StringBuilder();
                        JSONArray jsonArray = new JSONArray();

                        for (IEsptouchResult resultInList : result) {
                            if(!resultInList.isCancelled() &&resultInList.getBssid() != null) {
                                
                                sb.append("Esptouch success, bssid = ")
                                .append(resultInList.getBssid())
                                .append(", InetAddress = ")
                                .append(resultInList.getInetAddress().getHostAddress())
                                .append("\n");

                                JSONObject re = new JSONObject();
                                re.put("bssid", resultInList.getBssid());
                                re.put("ip", resultInList.getInetAddress().getHostAddress());
                                jsonArray.put(re);
                            }
                        }

                        Log.d(TAG, sb.toString());      
                        
                        JSONObject configureDeviceObj = new JSONObject();
                        configureDeviceObj.put("devices", jsonArray);
                        resultResp.success(configureDeviceObj.toString());

                    }  else {
                        resultResp.error("startSmartConfig", "Esptouch fail", null);
                    }
                } else {
                    resultResp.error("startSmartConfig", "Esptouch cancelled", null);
                } 

              } catch (Exception err) {
                  resultResp.error("startSmartConfig", err.getMessage(), null);
              }


          }
      }).execute(apSsid, apBssid, apPassword, deviceCountData, broadcastData);      
  }

  private int getBatteryLevel() {
    int batteryLevel = -1;
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      BatteryManager batteryManager = (BatteryManager) getSystemService(BATTERY_SERVICE);
      batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    } else {
      Intent intent = new ContextWrapper(getApplicationContext()).
          registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
      batteryLevel = (intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100) /
          intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
    }
  
    return batteryLevel;
  }

    public interface TaskListener {
        public void onFinished(List<IEsptouchResult> result);
    }

    private class EsptouchAsyncTask4 extends AsyncTask<byte[], Void, List<IEsptouchResult>> {
        private static final String TAG = "EsptouchAsyncTask4";

        private final TaskListener taskListener;

        public EsptouchAsyncTask4(TaskListener listener) {
            // The listener reference is passed in through the constructor
            this.taskListener = listener;
        }

        private final Object mLock = new Object();

        @Override
        protected void onPreExecute() {
            
        }

        @Override
        protected List<IEsptouchResult> doInBackground(byte[]... params) {
            int taskResultCount;

            synchronized (mLock) {
                byte[] apSsid = params[0];
                byte[] apBssid = params[1];
                byte[] apPassword = params[2];
                byte[] deviceCountData = params[3];
                byte[] broadcastData = params[4];

                taskResultCount = deviceCountData.length == 0 ? -1 : Integer.parseInt(new String(deviceCountData));

                mEsptouchTask = new EsptouchTask(apSsid, apBssid, apPassword,
                         getApplicationContext());
                mEsptouchTask.setPackageBroadcast(broadcastData[0] == 1); // true is broadcast, false is multicast
            }

            List<IEsptouchResult> resultList = mEsptouchTask.executeForResults(taskResultCount);
            return resultList;
        }

        @Override
        protected void onPostExecute(List<IEsptouchResult> result) {

            IEsptouchResult firstResult = result.get(0);
            // check whether the task is cancelled and no results received
            if (!firstResult.isCancelled()) {
                if(this.taskListener != null) {
                    // And if it is we call the callback function on it.
                    this.taskListener.onFinished(result);
                }
            }
        }
    }


}

