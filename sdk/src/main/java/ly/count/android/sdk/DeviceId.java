package ly.count.android.sdk;

import android.content.Context;
import android.util.Log;
import java.util.UUID;

/**
 * Created by artem on 07/11/14.
 */

@SuppressWarnings("DefaultFileTemplate")
public class DeviceId {
    /**
     * Enum used throughout Countly which controls what kind of ID Countly should use.
     */
    public enum Type {
        DEVELOPER_SUPPLIED,//custom value provided by the developer
        OPEN_UDID,//OPEN_UDID generated UDID
        ADVERTISING_ID,//id provided by the android OS
        TEMPORARY_ID,//temporary device ID mode
        RANDOM_UDID//fallback random UDID
    }

    private static final String TAG = "DeviceId";
    private static final String PREFERENCE_KEY_ID_ID = "ly.count.android.api.DeviceId.id";
    private static final String PREFERENCE_KEY_ID_TYPE = "ly.count.android.api.DeviceId.type";

    protected final static String temporaryCountlyDeviceId = "CLYTemporaryDeviceID";

    private String id;
    private Type type;

    /**
     * Initialize DeviceId with Type of OPEN_UDID or ADVERTISING_ID
     *
     * @param type type of ID generation strategy
     */
    protected DeviceId(CountlyStore store, Type type) {
        if (type == null) {
            throw new IllegalStateException("Please specify DeviceId.Type, that is which type of device ID generation you want to use");
        } else if (type == Type.DEVELOPER_SUPPLIED) {
            throw new IllegalStateException("Please use another DeviceId constructor for device IDs supplied by developer");
        }
        //setup the preferred device ID type
        this.type = type;

        //check if there wasn't a value set before
        retrieveId(store);
    }

    /**
     * Initialize DeviceId with Developer-supplied id string
     *
     * @param developerSuppliedId Device ID string supplied by developer
     */
    protected DeviceId(CountlyStore store, String developerSuppliedId) {
        if (developerSuppliedId == null || "".equals(developerSuppliedId)) {
            throw new IllegalStateException("Please make sure that device ID is not null or empty");
        }
        //setup the preferred device ID type
        this.type = Type.DEVELOPER_SUPPLIED;
        this.id = developerSuppliedId;

        //check if there wasn't a value set before
        retrieveId(store);
    }

    /**
     * Used during setup to retrieve the previously saved value
     * @param store
     */
    private void retrieveId(CountlyStore store) {
        //check if there is some stored value
        String storedId = store.getPreference(PREFERENCE_KEY_ID_ID);
        if (storedId != null) {
            //if there was a value saved previously, set it and it's type
            this.id = storedId;
            this.type = retrieveType(store, PREFERENCE_KEY_ID_TYPE);
        }
    }

    /**
     * Initialize device ID generation, that is start up required services and send requests.
     * Device ID is expected to be available after some time.
     * In some cases, Countly can override ID generation strategy to other one, for example when
     * Google Play Services are not available and user chose Advertising ID strategy, it will fall
     * back to OpenUDID
     *
     * @param context Context to use
     * @param store CountlyStore to store configuration in
     * @param raiseExceptions whether to raise exceptions in case of illegal state or not
     */
    protected void init(Context context, CountlyStore store, boolean raiseExceptions) {
        Type overriddenType = retrieveOverriddenType(store);

        // Some time ago some ID generation strategy was not available and SDK fell back to
        // some other strategy. We still have to use that strategy.
        if (overriddenType != null && overriddenType != type) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.i(TAG, "[DeviceId] Overridden device ID generation strategy detected: " + overriddenType + ", using it instead of " + this.type);
            }
            type = overriddenType;
        }

        switch (type) {
            case DEVELOPER_SUPPLIED:
                // no initialization for developer id
                // we just store the provided value so that it's
                store.setPreference(PREFERENCE_KEY_ID_ID, id);
                store.setPreference(PREFERENCE_KEY_ID_TYPE, type.toString());
                break;
            case OPEN_UDID:
                if (OpenUDIDAdapter.isOpenUDIDAvailable()) {
                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.i(TAG, "[DeviceId] Using OpenUDID");
                    }
                    if (!OpenUDIDAdapter.isInitialized()) {
                        OpenUDIDAdapter.sync(context);
                    }
                } else {
                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.w(TAG, "[DeviceId] Advertising ID is not available, switching to a random UDID");
                    }
                    GenerateRandomUDID(store);
                }
                break;
            case ADVERTISING_ID:
                if (AdvertisingIdAdapter.isAdvertisingIdAvailable()) {
                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.i(TAG, "[DeviceId] Using Advertising ID");
                    }
                    AdvertisingIdAdapter.setAdvertisingId(context, store, this);
                } else if (OpenUDIDAdapter.isOpenUDIDAvailable()) {
                    // Fall back to OpenUDID on devices without google play services set up
                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.i(TAG, "[DeviceId] Advertising ID is not available, falling back to OpenUDID");
                    }
                    if (!OpenUDIDAdapter.isInitialized()) {
                        OpenUDIDAdapter.sync(context);
                    }
                } else {
                    // use random ID, otherwise without Advertising ID and OpenUDID this user is lost for Countly
                    if (Countly.sharedInstance().isLoggingEnabled()) {
                        Log.w(TAG, "[DeviceId] Advertising ID is not available, neither OpenUDID is, switching to a random UDID");
                    }
                    GenerateRandomUDID(store);
                }
                break;
            case RANDOM_UDID:
                if(id == null) {
                    GenerateRandomUDID(store);
                }
                break;
        }
    }

    void GenerateRandomUDID(CountlyStore store){
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.w(TAG, "[DeviceId] Generating new random UDID");
        }

        //generated new ID and clear override
        String generatedID = UUID.randomUUID().toString();
        setId(Type.RANDOM_UDID, generatedID);

        store.setPreference(PREFERENCE_KEY_ID_ID, id);
        store.setPreference(PREFERENCE_KEY_ID_TYPE, type.toString());
    }

    private void storeOverriddenType(CountlyStore store, Type type) {
        // Using strings is safer when it comes to extending Enum values list
        store.setPreference(PREFERENCE_KEY_ID_TYPE, type == null ? null : type.toString());
    }

    private Type retrieveOverriddenType(CountlyStore store) {
        return retrieveType(store, PREFERENCE_KEY_ID_TYPE);
    }

    private Type retrieveType(CountlyStore store, String preferenceName) {
        // Using strings is safer when it comes to extending Enum values list
        String typeString = store.getPreference(preferenceName);
        if (typeString == null) {
            return null;
        } else if (typeString.equals(Type.DEVELOPER_SUPPLIED.toString())) {
            return Type.DEVELOPER_SUPPLIED;
        } else if (typeString.equals(Type.OPEN_UDID.toString())) {
            return Type.OPEN_UDID;
        } else if (typeString.equals(Type.ADVERTISING_ID.toString())) {
            return Type.ADVERTISING_ID;
        } else if (typeString.equals(Type.TEMPORARY_ID.toString())) {
            return Type.TEMPORARY_ID;
        } else if (typeString.equals(Type.RANDOM_UDID.toString())) {
            return Type.RANDOM_UDID;
        } else {
            return null;
        }
    }

    protected String getId() {
        if (id == null && type == Type.OPEN_UDID) {
            id = OpenUDIDAdapter.getOpenUDID();
        }
        return id;
    }

    @SuppressWarnings("SameParameterValue")
    protected void setId(Type type, String id) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.w(TAG, "[DeviceId] Device ID is " + id + " (type " + type + ")");
        }
        this.type = type;
        this.id = id;
    }

    @SuppressWarnings("SameParameterValue")
    protected void switchToIdType(Type type, Context context, CountlyStore store) {
        if (Countly.sharedInstance().isLoggingEnabled()) {
            Log.w(TAG, "[DeviceId] Switching to device ID generation strategy " + type + " from " + this.type);
        }
        this.type = type;
        storeOverriddenType(store, type);
        init(context, store, false);
    }

    protected String changeToDeveloperProvidedId(CountlyStore store, String newId) {

        String oldId = id == null || !id.equals(newId) ? id : null;

        id = newId;
        type = Type.DEVELOPER_SUPPLIED;

        store.setPreference(PREFERENCE_KEY_ID_ID, id);
        store.setPreference(PREFERENCE_KEY_ID_TYPE, type.toString());

        return oldId;
    }

    protected void changeToId(Context context, CountlyStore store, Type type, String deviceId) {
        this.id = deviceId;
        this.type = type;

        store.setPreference(PREFERENCE_KEY_ID_ID, deviceId);
        store.setPreference(PREFERENCE_KEY_ID_TYPE, type.toString());

        init(context, store, false);
    }

    /**
     * Returns the current type which would be returned to the developer
     * @return
     */
    protected Type getType() {
        if(temporaryIdModeEnabled()) {
            return Type.TEMPORARY_ID;
        }
        return type;
    }

    /**
     * Checks if temporary device ID mode is enabled by checking the currently set ID
     * @return
     */
    protected boolean temporaryIdModeEnabled() {
        String id = getId();
        if (id == null) {
            return false;
        }

        return id.equals(temporaryCountlyDeviceId);
    }

    /**
     * Helper method for null safe comparison of current device ID and the one supplied to Countly.init
     *
     * @return true if supplied device ID equal to the one registered before
     */
    static boolean deviceIDEqualsNullSafe(final String id, Type type, final DeviceId deviceId) {
        if (type == null || type == Type.DEVELOPER_SUPPLIED) {
            final String deviceIdId = deviceId == null ? null : deviceId.getId();
            return (deviceIdId == null && id == null) || (deviceIdId != null && deviceIdId.equals(id));
        } else {
            return true;
        }
    }
}
