package com.burnweb.rnwebview;

import android.annotation.SuppressLint;
import android.content.Intent;

import android.net.Uri;
import android.graphics.Bitmap;
import android.os.Build;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.facebook.react.common.SystemClock;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.UIManagerModule;
import com.facebook.react.uimanager.events.EventDispatcher;

import java.util.regex.Pattern;
import java.util.List;

class RNWebView extends WebView implements LifecycleEventListener {

    private final EventDispatcher mEventDispatcher;
    private final RNWebViewManager mViewManager;

    private String charset = "UTF-8";
    private String baseUrl = "file:///";
    private String injectedJavaScript = null;
    private boolean allowUrlRedirect = false;
    private boolean openLinkExternally = true;
    private ViewTreeObserver treeObserver;
    private boolean loading = true;
    private int contentHeight = 0;
    private List<String> filterURLPatterns = null;

    protected class EventWebClient extends WebViewClient {
        public boolean shouldOverrideUrlLoading(WebView view, String url){
            if (filterURLPatterns != null && filterURLPatterns.size() > 0) {
                // Stop navigating to the URL if filter pattern matches
                for (String filterPattern: filterURLPatterns) {
                    if (Pattern.matches(filterPattern, url)) {
                        // Notify JS side
                        mEventDispatcher.dispatchEvent(new URLFilteredEvent(getId(), url));
                        return true;
                    }
                }
            }
            if (RNWebView.this.getOpenLinkExternally()) {
                // Open link in external browser
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(url));
                view.getContext().startActivity(intent);
                return true;
            }
            if(RNWebView.this.getAllowUrlRedirect()) {
                // do your handling codes here, which url is the requested url
                // probably you need to open that url rather than redirect:
                view.loadUrl(url);

                return false; // then it is not handled by default action
            }

            return super.shouldOverrideUrlLoading(view, url);
        }

        public void onPageFinished(WebView view, String url) {
            RNWebView.this.loading = false;
            RNWebView.this.notifyJSNavigationState();

            if(RNWebView.this.getInjectedJavaScript() != null) {
                view.loadUrl("javascript:(function() {\n" + RNWebView.this.getInjectedJavaScript() + ";\n})();");
            }
        }

        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            RNWebView.this.loading = true;
            RNWebView.this.notifyJSNavigationState();
        }
    }

    protected class CustomWebChromeClient extends WebChromeClient {
        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            getModule().showAlert(url, message, result);
            return true;
        }

        // For Android 4.1+
        @SuppressWarnings("unused")
        public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
            getModule().startFileChooserIntent(uploadMsg, acceptType);
        }

        // For Android 5.0+
        @SuppressLint("NewApi")
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
            return getModule().startFileChooserIntent(filePathCallback, fileChooserParams.createIntent());
        }
    }

    protected class GeoWebChromeClient extends CustomWebChromeClient {
        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            callback.invoke(origin, true, false);
        }
    }

    public RNWebView(RNWebViewManager viewManager, ThemedReactContext reactContext) {
        super(reactContext);

        mViewManager = viewManager;
        mEventDispatcher = reactContext.getNativeModule(UIManagerModule.class).getEventDispatcher();

        this.getSettings().setJavaScriptEnabled(true);
        this.getSettings().setBuiltInZoomControls(false);
        this.getSettings().setDomStorageEnabled(true);
        this.getSettings().setGeolocationEnabled(false);
        this.getSettings().setPluginState(WebSettings.PluginState.ON);
        this.getSettings().setAllowFileAccess(true);
        this.getSettings().setAllowFileAccessFromFileURLs(true);
        this.getSettings().setAllowUniversalAccessFromFileURLs(true);
        this.getSettings().setLoadsImagesAutomatically(true);
        this.getSettings().setBlockNetworkImage(false);
        this.getSettings().setBlockNetworkLoads(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            this.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        this.setWebViewClient(new EventWebClient());
        this.setWebChromeClient(getCustomClient());
        // Trick to get the HTML content height and notify Javascript side
        treeObserver = this.getViewTreeObserver();
        treeObserver.addOnPreDrawListener(new OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                int height = RNWebView.this.getContentHeight();
                if (height > contentHeight) {
                    contentHeight = height;
                    notifyJSNavigationState();
                }
                return true;
            }
        });
    }

    /**
     * Send a notification message to Javascript
     */
    public void notifyJSNavigationState() {
        mEventDispatcher.dispatchEvent(new NavigationStateChangeEvent(getId(), SystemClock.uptimeMillis(),
            getTitle(), loading, getUrl(), canGoBack(), canGoForward(), contentHeight));
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public String getCharset() {
        return this.charset;
    }

    public void setOpenLinkExternally(boolean openLinkExternally) {
        this.openLinkExternally = openLinkExternally;
    }

    public boolean getOpenLinkExternally() {
        return this.openLinkExternally;
    }

    public void setAllowUrlRedirect(boolean a) {
        this.allowUrlRedirect = a;
    }

    public boolean getAllowUrlRedirect() {
        return this.allowUrlRedirect;
    }

    public void setInjectedJavaScript(String injectedJavaScript) {
        this.injectedJavaScript = injectedJavaScript;
    }

    public String getInjectedJavaScript() {
        return this.injectedJavaScript;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getBaseUrl() {
        return this.baseUrl;
    }

    public void setFilterURLPatterns(List<String> urls) {
        this.filterURLPatterns = urls;
    }

    public List<String> getFilterURLPatterns() {
        return this.filterURLPatterns;
    }

    public CustomWebChromeClient getCustomClient() {
        return new CustomWebChromeClient();
    }

    public GeoWebChromeClient getGeoClient() {
        return new GeoWebChromeClient();
    }

    public RNWebViewModule getModule() {
        return mViewManager.getPackage().getModule();
    }

    @Override
    public void onHostResume() {

    }

    @Override
    public void onHostPause() {

    }

    @Override
    public void onHostDestroy() {
        destroy();
    }

    @Override
    public void onDetachedFromWindow() {
        this.loadDataWithBaseURL(this.getBaseUrl(), "<html></html>", "text/html", this.getCharset(), null);
        super.onDetachedFromWindow();
    }

}
