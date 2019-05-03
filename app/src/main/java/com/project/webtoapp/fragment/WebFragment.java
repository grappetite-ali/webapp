package com.project.webtoapp.fragment;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.WebSettings.PluginState;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.reward.RewardItem;
import com.google.android.gms.ads.reward.RewardedVideoAd;
import com.google.android.gms.ads.reward.RewardedVideoAdListener;
import com.project.webtoapp.App;
import com.project.webtoapp.Config;
import com.project.webtoapp.util.GetFileInfo;
import com.project.webtoapp.R;
import com.project.webtoapp.widget.webview.WebToAppChromeClient;
import com.project.webtoapp.widget.webview.WebToAppWebClient;
import com.project.webtoapp.activity.MainActivity;
import com.project.webtoapp.widget.AdvancedWebView;
import com.project.webtoapp.widget.scrollable.ToolbarWebViewScrollListener;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutionException;

public class WebFragment extends Fragment implements AdvancedWebView.Listener, SwipeRefreshLayout.OnRefreshListener {
    //Layouts
    public FrameLayout rl;
    public AdvancedWebView browser;
    public SwipeRefreshLayout swipeLayout;
    public ProgressBar progressBar;

    //WebView Clients
    public WebToAppChromeClient chromeClient;
    public WebToAppWebClient webClient;

    //WebView Session
    public String mainUrl = null;
    static String URL = "url";
    public int firstLoad = 0;
    private boolean clearHistory = false;

    public WebFragment() {
        // Required empty public constructor
    }

    public static WebFragment newInstance(String url) {
        WebFragment fragment = new WebFragment();
        Bundle args = new Bundle();
        args.putString(URL, url);
        fragment.setArguments(args);
        return fragment;
    }

    public void setBaseUrl(String url) {
        this.mainUrl = url;
        this.clearHistory = true;
        browser.loadUrl(mainUrl);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null && mainUrl == null) {
            mainUrl = getArguments().getString(URL);
            firstLoad = 0;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        rl = (FrameLayout) inflater.inflate(R.layout.fragment_observable_web_view, container,
                false);

        progressBar = (ProgressBar) rl.findViewById(R.id.progressbar);
        browser = (AdvancedWebView) rl.findViewById(R.id.scrollable);
        swipeLayout = (SwipeRefreshLayout) rl.findViewById(R.id.swipe_container);

        return rl;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (Config.PULL_TO_REFRESH)
            swipeLayout.setOnRefreshListener(this);
        else
            swipeLayout.setEnabled(false);

        // Setting the webview listeners
        browser.setListener(this, this);

        // Setting the scroll listeners (if applicable)
        if (MainActivity.getCollapsingActionBar()) {

            ((MainActivity) getActivity()).showToolbar(this);

            browser.setOnScrollChangeListener(browser, new ToolbarWebViewScrollListener() {
                @Override
                public void onHide() {
                    ((MainActivity) getActivity()).hideToolbar();
                }

                @Override
                public void onShow() {
                    ((MainActivity) getActivity()).showToolbar(WebFragment.this);
                }
            });

        }

        // set javascript and zoom and some other settings
        browser.requestFocus();
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setBuiltInZoomControls(false);
        browser.getSettings().setAppCacheEnabled(true);
        browser.getSettings().setDatabaseEnabled(true);
        browser.getSettings().setDomStorageEnabled(true);
        // Below required for geolocation
        browser.setGeolocationEnabled(true);
        // 3RD party plugins (on older devices)
        browser.getSettings().setPluginState(PluginState.ON);

        if (Config.MULTI_WINDOWS) {
            browser.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
            browser.getSettings().setSupportMultipleWindows(true);
        }
        final AppmediationWebInterface appmediationJsInterface = new AppmediationWebInterface();
        appmediationJsInterface.registerInterface(browser);
        webClient = new WebToAppWebClient(this, browser) {
            @Override
            public void onPageFinished(final WebView view, String url) {
                super.onPageFinished(view, url);
                // Inject appmediation web interface into web view
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        appmediationJsInterface.init(getActivity(), view);
                    }
                });
            }
        };
        browser.setWebViewClient(webClient);

        chromeClient = new WebToAppChromeClient(this, rl, browser, swipeLayout, progressBar);
        browser.setWebChromeClient(chromeClient);

        // load url (if connection available
        if (webClient.hasConnectivity(mainUrl, true)) {
            String pushurl = ((App) getActivity().getApplication()).getPushUrl();
            if (pushurl != null) {
                browser.loadUrl(pushurl);
            } else {
                browser.loadUrl(mainUrl);
            }
        } else {
            try {
                ((MainActivity) getActivity()).hideSplash();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onRefresh() {
        browser.reload();
    }

    @SuppressLint("NewApi")
    @Override
    public void onPause() {
        super.onPause();
        browser.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        browser.onDestroy();
    }

    @SuppressLint("NewApi")
    @Override
    public void onResume() {
        super.onResume();
        browser.onResume();
    }

    @SuppressLint("NewApi")
    @Override
    public void onDownloadRequested(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
        if (!hasPermissionToDownload(getActivity())) return;

        String filename = null;
        try {
            filename = new GetFileInfo().execute(url).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        if (filename == null) {
            String fileExtenstion = MimeTypeMap.getFileExtensionFromUrl(url);
            filename = URLUtil.guessFileName(url, null, fileExtenstion);
        }


        if (AdvancedWebView.handleDownload(getActivity(), url, filename)) {
            Toast.makeText(getActivity(), getResources().getString(R.string.download_done), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getActivity(), getResources().getString(R.string.download_fail), Toast.LENGTH_SHORT).show();
        }
    }

    private static boolean hasPermissionToDownload(final Activity context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED)
            return true;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(R.string.download_permission_explaination);
        builder.setPositiveButton(R.string.common_permission_grant, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // Fire off an async request to actually get the permission
                // This will show the standard permission request dialog UI
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    context.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();

        return false;
    }

    @Override
    public void onPageStarted(String url, Bitmap favicon) {
        if (firstLoad == 0 && MainActivity.getCollapsingActionBar()) {
            ((MainActivity) getActivity()).showToolbar(this);
            firstLoad = 1;
        } else if (firstLoad == 0) {
            firstLoad = 1;
        }
    }

    @Override
    public void onPageFinished(String url) {
        if (!url.equals(mainUrl)
                && getActivity() != null
                && getActivity() instanceof MainActivity
                && Config.INTERSTITIAL_PAGE_LOAD)
            ((MainActivity) getActivity()).showInterstitial();

        try {
            ((MainActivity) getActivity()).hideSplash();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (clearHistory) {
            clearHistory = false;
            browser.clearHistory();
        }

        hideErrorScreen();
    }

    @Override
    public void onPageError(int errorCode, String description, String failingUrl) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onExternalPageRequest(String url) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        browser.onActivityResult(requestCode, resultCode, data);
    }

    // sharing
    public void shareURL() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        String appName = getString(R.string.app_name);
        shareIntent
                .putExtra(
                        Intent.EXTRA_TEXT,
                        String.format(getString(R.string.share_body), browser.getTitle(), appName + " https://play.google.com/store/apps/details?id=" + getActivity().getPackageName()));
        startActivity(Intent.createChooser(shareIntent,
                getText(R.string.sharetitle)));
    }

    public void showErrorScreen(String message) {
        final View stub = rl.findViewById(R.id.empty_view);
        stub.setVisibility(View.VISIBLE);

        ((TextView) stub.findViewById(R.id.title)).setText(message);
        stub.findViewById(R.id.retry_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (browser.getUrl() == null) {
                    browser.loadUrl(mainUrl);
                } else {
                    browser.loadUrl("javascript:document.open();document.close();");
                    browser.reload();
                }
            }
        });
    }

    public void hideErrorScreen() {
        final View stub = rl.findViewById(R.id.empty_view);
        if (stub.getVisibility() == View.VISIBLE)
            stub.setVisibility(View.GONE);
    }

    private static class AppmediationWebInterface implements RewardedVideoAdListener {
        private RewardedVideoAd mRewardedVideoAd;
        private static final String INTERFACE_NAME = "AppmediationSDK";
        private WeakReference<Activity> activityRef;
        private WeakReference<WebView> webViewRef;
        private MediaPlayer btnClickPlayer;

        public void init(Activity activity, WebView webView) {
            this.activityRef = new WeakReference<>(activity);
            this.webViewRef = new WeakReference<>(webView);
            registerInterface();
            injectJs("appmediationJsInterfaceInit();");
            mRewardedVideoAd = MobileAds.getRewardedVideoAdInstance(activity);
            mRewardedVideoAd.setRewardedVideoAdListener(this);
            loadRewardedVideoAd();

        }

        private void loadRewardedVideoAd() {
            mRewardedVideoAd.loadAd(activityRef.get().getString(R.string.admob_reward_unit_id),
                    new AdRequest.Builder().build());
        }

        private void registerInterface() {
            registerInterface(webViewRef != null ? webViewRef.get() : null);
        }

        @SuppressLint("AddJavascriptInterface")
        private void registerInterface(WebView webView) {
            if (webView == null) return;
            webView.addJavascriptInterface(this, INTERFACE_NAME);
        }

        @JavascriptInterface
        public void rewardedVideoRequest() {
            if (activityRef == null) return;
            activityRef.get().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mRewardedVideoAd.show();
                }
            });
        }

        @JavascriptInterface
        public void playAudio(String type) {
            if (btnClickPlayer != null)
                btnClickPlayer.stop();
            switch (type) {
                case "btn1": {
                    btnClickPlayer = MediaPlayer.create(activityRef.get(), R.raw.poker);
                    btnClickPlayer.start();
                }
                break;
                case "btn2": {
                    btnClickPlayer = MediaPlayer.create(activityRef.get(), R.raw.bell);
                    btnClickPlayer.start();
                }
                case "btn3": {
                    btnClickPlayer = MediaPlayer.create(activityRef.get(), R.raw.van);
                    btnClickPlayer.start();
                }
                break;
            }
        }

        private void showRewardedVideo() {
            if (mRewardedVideoAd.isLoaded()) {
                mRewardedVideoAd.show();
            }
        }

        @JavascriptInterface
        public boolean isRewardedVideoAvailable() {
            return mRewardedVideoAd.isLoaded();
        }


        @Override
        public void onRewardedVideoAdLoaded() {
            Toast.makeText(activityRef.get(), "Video add loaded. Now you can play it", Toast.LENGTH_SHORT).show();
            injectJs("onRewardedVideoLoaded();");

        }

        @Override
        public void onRewardedVideoAdOpened() {
            injectJs("onRewardedVideoOpened();");

        }

        @Override
        public void onRewardedVideoStarted() {
            injectJs("onRewardedVideoStarted();");

        }

        @Override
        public void onRewardedVideoAdClosed() {
            injectJs("onRewardedVideoClosed();");
            loadRewardedVideoAd();
        }

        @Override
        public void onRewarded(RewardItem reward) {
            injectJs("onRewardedVideoCompleted('" + reward + "');");

        }

        @Override
        public void onRewardedVideoAdLeftApplication() {

        }

        @Override
        public void onRewardedVideoAdFailedToLoad(int i) {
            injectJs("onRewardedVideoFailedToLoad();");

        }

        @Override
        public void onRewardedVideoCompleted() {
            injectJs("onRewardedVideoCompleted('Completed watched');");

        }


        private void injectJs(String javaScript) {
            WebView webView = webViewRef != null ? webViewRef.get() : null;
            if (webView == null) return;
            /*webView.loadUrl("javascript:(function() { "
                    + javaScript +
                    "})()");*/
            webView.loadUrl("javascript:" + javaScript);
        }
    }
}
