package com.project.webtoapp;

import android.content.Intent;
import android.net.Uri;
import android.support.multidex.MultiDexApplication;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Tracker;
import com.onesignal.OSNotificationOpenResult;
import com.onesignal.OneSignal;
import com.project.webtoapp.activity.MainActivity;

import org.json.JSONObject;

public class App extends MultiDexApplication {
	
	  public static GoogleAnalytics analytics;
	  public static Tracker tracker;

      private String push_url = null;

    @Override public void onCreate() {
        super.onCreate();

        if (Config.ANALYTICS_ID.length() > 0) {
            analytics = GoogleAnalytics.getInstance(this);
            analytics.setLocalDispatchPeriod(1800);

            tracker = analytics.newTracker(Config.ANALYTICS_ID); // Replace with actual tracker/property Id
            tracker.enableExceptionReporting(true);
            tracker.enableAdvertisingIdCollection(true);
            tracker.enableAutoActivityTracking(true);
        }

        //OneSignal Push
        if (!TextUtils.isEmpty(getString(R.string.onesignal_app_id)))
            OneSignal.init(this, "REMOTE", getString(R.string.onesignal_app_id), new NotificationHandler());
    }

    // This fires when a notification is opened by tapping on it or one is received while the app is running.
    private class NotificationHandler implements OneSignal.NotificationOpenedHandler {
        // This fires when a notification is opened by tapping on it.
        @Override
        public void notificationOpened(OSNotificationOpenResult result) {
            try {
                JSONObject data = result.notification.payload.additionalData;

                String webViewUrl = (data != null) ? data.optString("url", null) : null;
                String browserUrl = result.notification.payload.launchURL;

                if (webViewUrl != null || browserUrl != null) {
                    if (browserUrl != null || result.notification.isAppInFocus) {
                        browserUrl = (browserUrl == null) ? webViewUrl : browserUrl;
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(browserUrl));
                        startActivity(browserIntent);
                        Log.v("INFO", "Received notification while app was on foreground or url for browser");
                    } else {
                        push_url = webViewUrl;
                    }
                } else if (!result.notification.isAppInFocus) {
                    Intent mainIntent;
                    mainIntent = new Intent(App.this, MainActivity.class);
                    mainIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(mainIntent);
                }


            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

    }

    public synchronized String getPushUrl(){
        String url = push_url;
        push_url = null;
        return url;
    }

    public synchronized void setPushUrl(String url){
        this.push_url = url;
    }
} 