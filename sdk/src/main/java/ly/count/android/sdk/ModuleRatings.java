package ly.count.android.sdk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Debug;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.RatingBar;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ModuleRatings extends ModuleBase {
    static final String STAR_RATING_EVENT_KEY = "[CLY]_star_rating";

    //star rating
    StarRatingCallback starRatingCallback_;// saved callback that is used for automatic star rating
    boolean showStarRatingDialogOnFirstActivity = false;

    final Ratings ratingsInterface;

    ModuleRatings(Countly cly, CountlyConfig config) {
        super(cly);

        if (_cly.isLoggingEnabled()) {
            Log.v(Countly.TAG, "[ModuleRatings] Initialising");
        }

        starRatingCallback_ = config.starRatingCallback;
        setStarRatingInitConfig(config.countlyStore, config.starRatingSessionLimit, config.starRatingTextTitle, config.starRatingTextMessage, config.starRatingTextDismiss);
        setIfRatingDialogIsCancellableInternal(config.countlyStore, config.starRatingDialogIsCancellable);
        setShowDialogAutomatically(config.countlyStore, config.starRatingShownAutomatically);
        setStarRatingDisableAskingForEachAppVersion(config.countlyStore, config.starRatingDisableAskingForEachAppVersion);

        ratingsInterface = new Ratings();
    }

    void recordManualRatingInternal(String widgetId, int rating, String email, String comment, boolean userCanBeContacted) {
        if (_cly.isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleRatings] Calling recordManualRatingInternal");
        }

        if (!Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.starRating)) {
            return;
        }

        if (widgetId == null) {
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[ModuleRatings] recordManualRatingInternal, provided widget ID is null, returning");
            }
            return;
        }

        if (widgetId.isEmpty()) {
            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[ModuleRatings] recordManualRatingInternal, provided widget ID is empty, returning");
            }
            return;
        }

        if (rating < 0) {
            rating = 0;

            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[ModuleRatings] recordManualRatingInternal, given rating too low, defaulting to 0");
            }
        }

        if (rating > 5) {
            rating = 5;

            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[ModuleRatings] recordManualRatingInternal, given rating too high, defaulting to 5");
            }
        }

        Map<String, Object> segm = new HashMap<>();
        segm.put("platform", "android");
        segm.put("app_version", DeviceInfo.getAppVersion(_cly.context_));
        segm.put("rating", "" + rating);
        segm.put("widget_id", widgetId);
        segm.put("contactMe", userCanBeContacted);

        if (email != null && !email.isEmpty()) {
            segm.put("email", email);
        }

        if (comment != null && !comment.isEmpty()) {
            segm.put("comment", comment);
        }

        _cly.moduleEvents.recordEventInternal(ModuleRatings.STAR_RATING_EVENT_KEY, segm, 1, 0, 0, null, true);
    }

    /**
     * Save the star rating preferences object
     *
     * @param srp
     */
    private void saveStarRatingPreferences(final CountlyStore cs, final StarRatingPreferences srp) {
        cs.setStarRatingPreferences(srp.toJSON().toString());
    }

    /**
     * Setting things that would be provided during initial config
     *
     * @param limit limit for automatic rating
     * @param starRatingTextTitle provided title
     * @param starRatingTextMessage provided message
     * @param starRatingTextDismiss provided dismiss text
     */
    void setStarRatingInitConfig(final CountlyStore cs, final int limit, final String starRatingTextTitle, final String starRatingTextMessage, final String starRatingTextDismiss) {
        StarRatingPreferences srp = loadStarRatingPreferences(cs);

        if (limit >= 0) {
            srp.sessionLimit = limit;
        }

        if (starRatingTextTitle != null) {
            srp.dialogTextTitle = starRatingTextTitle;
        }

        if (starRatingTextMessage != null) {
            srp.dialogTextMessage = starRatingTextMessage;
        }

        if (starRatingTextDismiss != null) {
            srp.dialogTextDismiss = starRatingTextDismiss;
        }

        saveStarRatingPreferences(cs, srp);
    }

    /**
     * Set if the star rating dialog should be shown automatically
     *
     * @param shouldShow
     */
    void setShowDialogAutomatically(final CountlyStore cs, final boolean shouldShow) {
        StarRatingPreferences srp = loadStarRatingPreferences(cs);
        srp.automaticRatingShouldBeShown = shouldShow;
        saveStarRatingPreferences(cs, srp);
    }

    boolean getIfStarRatingShouldBeShownAutomatically() {
        StarRatingPreferences srp = loadStarRatingPreferences(_cly.eventQueue_.getCountlyStore());
        return srp.automaticRatingShouldBeShown;
    }

    /**
     * Set if automatic star rating should be disabled for each new version.
     * By default automatic star rating will be shown for every new app version.
     * If this is set to true, star rating will be shown only once over apps lifetime
     *
     * @param disableAsking if set true, will not show star rating for every new app version
     */
    void setStarRatingDisableAskingForEachAppVersion(final CountlyStore cs, final boolean disableAsking) {
        StarRatingPreferences srp = loadStarRatingPreferences(cs);
        srp.disabledAutomaticForNewVersions = disableAsking;
        saveStarRatingPreferences(cs, srp);
    }

    /**
     * Register that a apps session has transpired. Will increase session counter and show automatic star rating if needed.
     *
     * @param context android context
     * @param starRatingCallback
     */
    void registerAppSession(final Context context, final CountlyStore cs, final StarRatingCallback starRatingCallback) {
        StarRatingPreferences srp = loadStarRatingPreferences(cs);

        String currentAppVersion = DeviceInfo.getAppVersion(context);

        //a new app version is released, reset all counters
        //if we show the rating once per apps lifetime, don't reset the counters
        if (currentAppVersion != null && !currentAppVersion.equals(srp.appVersion) && !srp.disabledAutomaticForNewVersions) {
            srp.appVersion = currentAppVersion;
            srp.isShownForCurrentVersion = false;
            srp.sessionAmount = 0;
        }

        srp.sessionAmount++;
        if (srp.sessionAmount >= srp.sessionLimit && !srp.isShownForCurrentVersion && srp.automaticRatingShouldBeShown && !(srp.disabledAutomaticForNewVersions && srp.automaticHasBeenShown)) {
            showStarRatingDialogOnFirstActivity = true;
        }

        saveStarRatingPreferences(cs, srp);
    }

    /**
     * Returns the session limit set for automatic star rating
     */
    static int getAutomaticStarRatingSessionLimitInternal(final CountlyStore cs) {
        StarRatingPreferences srp = loadStarRatingPreferences(cs);
        return srp.sessionLimit;
    }

    /**
     * Returns how many sessions has star rating counted internally
     *
     * @return
     */
    int getCurrentVersionsSessionCountInternal(final CountlyStore cs) {
        StarRatingPreferences srp = loadStarRatingPreferences(cs);
        return srp.sessionAmount;
    }

    /**
     * Set the automatic star rating session count back to 0
     */
    void clearAutomaticStarRatingSessionCountInternal(final CountlyStore cs) {
        StarRatingPreferences srp = loadStarRatingPreferences(cs);
        srp.sessionAmount = 0;
        saveStarRatingPreferences(cs, srp);
    }

    /**
     * Set if the star rating dialog is cancellable
     *
     * @param isCancellable
     */
    void setIfRatingDialogIsCancellableInternal(final CountlyStore cs, final boolean isCancellable) {
        StarRatingPreferences srp = loadStarRatingPreferences(cs);
        srp.isDialogCancellable = isCancellable;
        saveStarRatingPreferences(cs, srp);
    }

    /**
     * Class that handles star rating internal state
     */
    static class StarRatingPreferences {
        String appVersion = ""; //the name of the current version that we keep track of
        int sessionLimit = 5; //session limit for the automatic star rating
        int sessionAmount = 0; //session amount for the current version
        boolean isShownForCurrentVersion = false; //if automatic star rating has been shown for the current version
        boolean automaticRatingShouldBeShown = false; //if the automatic star rating should be shown
        boolean disabledAutomaticForNewVersions = false; //if the automatic star star should not be shown for every new apps version
        boolean automaticHasBeenShown = false; //if automatic star rating has been shown for any app's version
        boolean isDialogCancellable = true; //if star rating dialog is cancellable
        String dialogTextTitle = "App rating";
        String dialogTextMessage = "Please rate this app";
        String dialogTextDismiss = "Cancel";

        private static final String KEY_APP_VERSION = "sr_app_version";
        private static final String KEY_SESSION_LIMIT = "sr_session_limit";
        private static final String KEY_SESSION_AMOUNT = "sr_session_amount";
        private static final String KEY_IS_SHOWN_FOR_CURRENT = "sr_is_shown";
        private static final String KEY_AUTOMATIC_RATING_IS_SHOWN = "sr_is_automatic_shown";
        private static final String KEY_DISABLE_AUTOMATIC_NEW_VERSIONS = "sr_is_disable_automatic_new";
        private static final String KEY_AUTOMATIC_HAS_BEEN_SHOWN = "sr_automatic_has_been_shown";
        private static final String KEY_DIALOG_IS_CANCELLABLE = "sr_automatic_dialog_is_cancellable";
        private static final String KEY_DIALOG_TEXT_TITLE = "sr_text_title";
        private static final String KEY_DIALOG_TEXT_MESSAGE = "sr_text_message";
        private static final String KEY_DIALOG_TEXT_DISMISS = "sr_text_dismiss";

        /**
         * Create a JSONObject from the current state
         *
         * @return
         */
        JSONObject toJSON() {
            final JSONObject json = new JSONObject();

            try {
                json.put(KEY_APP_VERSION, appVersion);
                json.put(KEY_SESSION_LIMIT, sessionLimit);
                json.put(KEY_SESSION_AMOUNT, sessionAmount);
                json.put(KEY_IS_SHOWN_FOR_CURRENT, isShownForCurrentVersion);
                json.put(KEY_AUTOMATIC_RATING_IS_SHOWN, automaticRatingShouldBeShown);
                json.put(KEY_DISABLE_AUTOMATIC_NEW_VERSIONS, disabledAutomaticForNewVersions);
                json.put(KEY_AUTOMATIC_HAS_BEEN_SHOWN, automaticHasBeenShown);
                json.put(KEY_DIALOG_IS_CANCELLABLE, isDialogCancellable);
                json.put(KEY_DIALOG_TEXT_TITLE, dialogTextTitle);
                json.put(KEY_DIALOG_TEXT_MESSAGE, dialogTextMessage);
                json.put(KEY_DIALOG_TEXT_DISMISS, dialogTextDismiss);
            } catch (JSONException e) {
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    Log.w(Countly.TAG, "Got exception converting an StarRatingPreferences to JSON", e);
                }
            }

            return json;
        }

        /**
         * Load the preference state from a JSONObject
         *
         * @param json
         * @return
         */
        static StarRatingPreferences fromJSON(final JSONObject json) {

            StarRatingPreferences srp = new StarRatingPreferences();

            if (json != null) {
                try {
                    srp.appVersion = json.getString(KEY_APP_VERSION);
                    srp.sessionLimit = json.optInt(KEY_SESSION_LIMIT, 5);
                    srp.sessionAmount = json.optInt(KEY_SESSION_AMOUNT, 0);
                    srp.isShownForCurrentVersion = json.optBoolean(KEY_IS_SHOWN_FOR_CURRENT, false);
                    srp.automaticRatingShouldBeShown = json.optBoolean(KEY_AUTOMATIC_RATING_IS_SHOWN, true);
                    srp.disabledAutomaticForNewVersions = json.optBoolean(KEY_DISABLE_AUTOMATIC_NEW_VERSIONS, false);
                    srp.automaticHasBeenShown = json.optBoolean(KEY_AUTOMATIC_HAS_BEEN_SHOWN, false);
                    srp.isDialogCancellable = json.optBoolean(KEY_DIALOG_IS_CANCELLABLE, true);

                    if (!json.isNull(KEY_DIALOG_TEXT_TITLE)) {
                        srp.dialogTextTitle = json.getString(KEY_DIALOG_TEXT_TITLE);
                    }

                    if (!json.isNull(KEY_DIALOG_TEXT_MESSAGE)) {
                        srp.dialogTextMessage = json.getString(KEY_DIALOG_TEXT_MESSAGE);
                    }

                    if (!json.isNull(KEY_DIALOG_TEXT_DISMISS)) {
                        srp.dialogTextDismiss = json.getString(KEY_DIALOG_TEXT_DISMISS);
                    }
                } catch (JSONException e) {
                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.w(Countly.TAG, "Got exception converting JSON to a StarRatingPreferences", e);
                    }
                }
            }

            return srp;
        }
    }

    /**
     * Call to manually show star rating dialog
     *
     * @param context android context
     * @param callback
     */
    void showStarRatingInternal(final Context context, final CountlyStore cs, final StarRatingCallback callback) {
        StarRatingPreferences srp = loadStarRatingPreferences(cs);
        showStarRatingCustom(context, srp.dialogTextTitle, srp.dialogTextMessage, srp.dialogTextDismiss, srp.isDialogCancellable, callback);
    }

    /**
     * Returns a object with the loaded preferences
     *
     * @return
     */
    static StarRatingPreferences loadStarRatingPreferences(final CountlyStore cs) {
        String srpString = cs.getStarRatingPreferences();
        StarRatingPreferences srp;

        if (!srpString.equals("")) {
            JSONObject srJSON;
            try {
                srJSON = new JSONObject(srpString);
                srp = StarRatingPreferences.fromJSON(srJSON);
            } catch (JSONException e) {
                e.printStackTrace();
                srp = new StarRatingPreferences();
            }
        } else {
            srp = new StarRatingPreferences();
        }
        return srp;
    }

    /**
     * Method that created the star rating dialog
     *
     * @param context android context
     * @param title
     * @param message
     * @param cancelText
     * @param isCancellable
     * @param callback
     */
    void showStarRatingCustom(final Context context, final String title, final String message, final String cancelText, final boolean isCancellable, final StarRatingCallback callback) {
        if (!(context instanceof Activity)) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.e(Countly.TAG, "[ModuleRatings] Can't show star rating dialog, the provided context is not based off a activity");
            }

            return;
        }

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View dialogLayout = inflater.inflate(R.layout.star_rating_layout, null);
        RatingBar ratingBar = dialogLayout.findViewById(R.id.ratingBar);

        final AlertDialog.Builder builder = new AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(isCancellable)
            .setView(dialogLayout)
            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialogInterface) {
                    if (callback != null) {
                        //call the dismiss callback ir the user clicks the back button or clicks outside the dialog
                        callback.onDismiss();
                    }
                }
            })
            .setPositiveButton(cancelText, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    if (callback != null) {
                        //call the dismiss callback if the user clicks the "dismiss" button
                        callback.onDismiss();
                    }
                }
            });

        final AlertDialog dialog = builder.show();

        ratingBar.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener() {
            @Override
            public void onRatingChanged(RatingBar ratingBar, float v, boolean b) {
                int rating = (int) v;

                if (Countly.sharedInstance().getConsent(Countly.CountlyFeatureNames.starRating)) {
                    Map<String, String> segm = new HashMap<>();
                    segm.put("platform", "android");
                    segm.put("app_version", DeviceInfo.getAppVersion(context));
                    segm.put("rating", "" + rating);

                    Countly.sharedInstance().recordEvent(ModuleRatings.STAR_RATING_EVENT_KEY, segm, 1);
                }

                dialog.dismiss();
                if (callback != null) {
                    callback.onRate(rating);
                }
            }
        });
    }

    /// Countly webDialog user rating

    synchronized void showFeedbackPopupInternal(final String widgetId, final String closeButtonText, final Activity activity, final FeedbackRatingCallback devCallback) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleRatings] Showing Feedback popup for widget id: [" + widgetId + "]");
        }

        if (widgetId == null || widgetId.isEmpty()) {
            if (devCallback != null) {
                devCallback.callback("Countly widgetId cannot be null or empty");
            }
            Log.e(Countly.TAG, "[ModuleRatings] Countly widgetId cannot be null or empty");
            return;
        }

        if (activity == null) {
            if (devCallback != null) {
                devCallback.callback("When showing feedback popup, Activity can't be null");
            }
            Log.e(Countly.TAG, "[ModuleRatings] When showing feedback popup, Activity can't be null");
            return;
        }

        if (!_cly.getConsent(Countly.CountlyFeatureNames.starRating)) {
            if (devCallback != null) {
                devCallback.callback("Consent is not granted");
            }
            return;
        }

        //check the device type
        final boolean deviceIsPhone;
        final boolean deviceIsTablet;
        final boolean deviceIsTv;

        deviceIsTv = Utils.isDeviceTv(activity);

        if (!deviceIsTv) {
            deviceIsPhone = !Utils.isDeviceTablet(activity);
            deviceIsTablet = Utils.isDeviceTablet(activity);
        } else {
            deviceIsTablet = false;
            deviceIsPhone = false;
        }

        String requestData = _cly.connectionQueue_.prepareRatingWidgetRequest(widgetId);
        final String ratingWidgetUrl = _cly.connectionQueue_.getServerURL() + "/feedback?widget_id=" + widgetId + "&device_id=" + _cly.connectionQueue_.getDeviceId().getId() + "&app_key=" + _cly.connectionQueue_.getAppKey();

        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.d(Countly.TAG, "[ModuleRatings] rating widget url :[" + ratingWidgetUrl + "]");
        }

        ConnectionProcessor cp = _cly.connectionQueue_.createConnectionProcessor();

        (new ImmediateRequestMaker()).execute(requestData, "/o/feedback/widget", cp, false, new ImmediateRequestMaker.InternalFeedbackRatingCallback() {
            @Override
            public void callback(JSONObject checkResponse) {
                if (checkResponse == null) {
                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.d(Countly.TAG, "[ModuleRatings] Not possible to show Feedback popup for widget id: [" + widgetId + "], probably a lack of connection to the server");
                    }
                    if (devCallback != null) {
                        devCallback.callback("Not possible to show Rating popup, probably no internet connection");
                    }
                    return;
                }

                try {
                    JSONObject jDevices = checkResponse.getJSONObject("target_devices");

                    boolean showOnTv = jDevices.optBoolean("desktop", false);
                    boolean showOnPhone = jDevices.optBoolean("phone", false);
                    boolean showOnTablet = jDevices.optBoolean("tablet", false);

                    if ((deviceIsPhone && showOnPhone) || (deviceIsTablet && showOnTablet) || (deviceIsTv && showOnTv)) {
                        //it's possible to show the rating window on this device
                        if (Countly.sharedInstance().isLoggingEnabled()) {
                            Log.d(Countly.TAG, "[ModuleRatings] Showing Feedback popup for widget id: [" + widgetId + "]");
                        }

                        RatingDialogWebView webView = new RatingDialogWebView(activity);
                        webView.getSettings().setJavaScriptEnabled(true);
                        webView.loadUrl(ratingWidgetUrl);

                        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                        builder.setView(webView);
                        if (closeButtonText != null && !closeButtonText.isEmpty()) {
                            builder.setNeutralButton(closeButtonText, null);
                        }
                        builder.show();
                    } else {
                        if (devCallback != null) {
                            devCallback.callback("Rating dialog is not meant for this form factor");
                        }
                    }
                } catch (JSONException e) {
                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    static class RatingDialogWebView extends WebView {
        public RatingDialogWebView(Context context) {
            super(context);
        }

        /**
         * Without this override, the keyboard is not showing
         */
        @Override
        public boolean onCheckIsTextEditor() {
            return true;
        }
    }

    static class FeedbackDialogWebViewClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            Log.i(Countly.TAG, "attempting to load resource: " + url);
            return null;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Log.i(Countly.TAG, "attempting to load resource: " + request.getUrl());
            }
            return null;
        }
    }

    @Override
    void callbackOnActivityResumed(Activity activity) {
        if (showStarRatingDialogOnFirstActivity) {
            CountlyStore cs = _cly.connectionQueue_.getCountlyStore();
            StarRatingPreferences srp = loadStarRatingPreferences(cs);
            srp.isShownForCurrentVersion = true;
            srp.automaticHasBeenShown = true;

            showStarRatingInternal(activity, cs, starRatingCallback_);

            saveStarRatingPreferences(cs, srp);
            showStarRatingDialogOnFirstActivity = false;
        }
    }

    @Override
    void halt() {

    }

    public class Ratings {
        /**
         * Record user rating manually without showing any message dialog.
         *
         * @param widgetId widget ID to which this rating will be tied. You get it from the dashboard
         * @param rating value from 0 to 5 that will be set as the rating value
         * @param email email of the user
         * @param comment comment set by the user
         * @param userCanBeContacted set true if the user wants you to contact him
         */
        public void recordManualRating(String widgetId, int rating, String email, String comment, boolean userCanBeContacted) {
            synchronized (_cly) {
                if (_cly.isLoggingEnabled()) {
                    Log.i(Countly.TAG, "[Ratings] Calling recordManualRating");
                }

                if (widgetId == null || widgetId.isEmpty()) {
                    throw new IllegalStateException("A valid widgetID must be provided. The current one is either null or empty");
                }

                recordManualRatingInternal(widgetId, rating, email, comment, userCanBeContacted);
            }
        }

        /**
         * Show the rating dialog to the user
         *
         * @param widgetId ID that identifies this dialog
         * @return
         */
        public void showFeedbackPopup(final String widgetId, final String closeButtonText, final Activity activity, final FeedbackRatingCallback callback) {
            synchronized (_cly) {
                if (_cly.isLoggingEnabled()) {
                    Log.i(Countly.TAG, "[Ratings] Calling showFeedbackPopup");
                }

                showFeedbackPopupInternal(widgetId, closeButtonText, activity, callback);
            }
        }

        /**
         * Shows the star rating dialog
         *
         * @param activity the activity that will own the dialog
         * @param callback callback for the star rating dialog "rate" and "dismiss" events
         */
        public void showStarRating(Activity activity, StarRatingCallback callback) {
            synchronized (_cly) {
                if (_cly.isLoggingEnabled()) {
                    Log.i(Countly.TAG, "[Ratings] Calling showStarRating");
                }

                if (!_cly.getConsent(Countly.CountlyFeatureNames.starRating)) {
                    return;
                }

                showStarRatingInternal(activity, _cly.connectionQueue_.getCountlyStore(), callback);
            }
        }

        /**
         * Returns how many sessions has star rating counted internally for the current apps version
         *
         * @return
         */
        public int getCurrentVersionsSessionCount() {
            synchronized (_cly) {
                int sessionCount = getCurrentVersionsSessionCountInternal(_cly.connectionQueue_.getCountlyStore());

                if (_cly.isLoggingEnabled()) {
                    Log.i(Countly.TAG, "[Ratings] Getting star rating current version session count: [" + sessionCount + "]");
                }

                return sessionCount;
            }
        }

        /**
         * Set the automatic star rating session count back to 0
         */
        public void clearAutomaticStarRatingSessionCount() {
            synchronized (_cly) {
                if (_cly.isLoggingEnabled()) {
                    Log.i(Countly.TAG, "[Ratings] Clearing star rating session count");
                }

                clearAutomaticStarRatingSessionCountInternal(_cly.connectionQueue_.getCountlyStore());
            }
        }

        /**
         * Returns the session limit set for automatic star rating
         */
        public int getAutomaticStarRatingSessionLimit() {
            synchronized (_cly) {
                int sessionLimit = ModuleRatings.getAutomaticStarRatingSessionLimitInternal(_cly.connectionQueue_.getCountlyStore());

                if (_cly.isLoggingEnabled()) {
                    Log.i(Countly.TAG, "[Ratings] Getting automatic star rating session limit: [" + sessionLimit + "]");
                }

                return sessionLimit;
            }
        }
    }
}
