package ly.count.android.sdk;

import android.content.Intent;
import android.util.Log;

public class ModuleConsent extends ModuleBase {

    Consent consentInterface = null;

    ModuleConsent(Countly cly, CountlyConfig config) {
        super(cly);

        if (_cly.isLoggingEnabled()) {
            Log.v(Countly.TAG, "[ModuleConsent] Initialising");
        }

        consentInterface = new Consent();
    }

    @Override
    void initFinished(CountlyConfig config) {
        if (_cly.requiresConsent) {
            //do delayed push consent action, if needed
            if (_cly.delayedPushConsent != null) {
                _cly.doPushConsentSpecialAction(_cly.delayedPushConsent);
            }

            //do delayed location erasure, if needed
            if (_cly.delayedLocationErasure) {
                _cly.doLocationConsentSpecialErasure();
            }

            //send collected consent changes that were made before initialization
            if (_cly.collectedConsentChanges.size() != 0) {
                for (String changeItem : _cly.collectedConsentChanges) {
                    _cly.connectionQueue_.sendConsentChanges(changeItem);
                }
                _cly.collectedConsentChanges.clear();
            }

            _cly.context_.sendBroadcast(new Intent(_cly.CONSENT_BROADCAST));

            if (_cly.isLoggingEnabled()) {
                Log.d(Countly.TAG, "[ModuleConsent] [Init] Countly is initialized with the current consent state:");
                _cly.checkAllConsent();
            }
        }
    }

    @Override
    void halt() {
        consentInterface = null;
    }

    public class Consent {
        /**
         * Print the consent values of all features
         *
         * @return Returns link to Countly for call chaining
         */
        public void checkAllConsent() {
            synchronized (_cly) {
                if (_cly.isLoggingEnabled()) {
                    Log.i(Countly.TAG, "[Consent] calling checkAllConsent");
                }

                _cly.checkAllConsent();
            }
        }

        /**
         * Get the current consent state of a feature
         *
         * @param featureName the name of a feature for which consent should be checked
         * @return the consent value
         */
        public boolean getConsent(String featureName) {
            synchronized (_cly) {
                return _cly.getConsent(featureName);
            }
        }

        /**
         * Remove consent for all features
         *
         * @return Returns link to Countly for call chaining
         */
        public void removeConsentAll() {
            synchronized (_cly) {
                _cly.removeConsentAll();
            }
        }

        /**
         * Remove the consent of a feature
         *
         * @param featureNames the names of features for which consent should be removed
         * @return Returns link to Countly for call chaining
         */
        public void removeConsent(String[] featureNames) {
            synchronized (_cly) {
                _cly.removeConsent(featureNames);
            }
        }

        /**
         * Gives consent for all features
         *
         * @return Returns link to Countly for call chaining
         */
        public void giveConsentAll() {
            synchronized (_cly) {
                if (_cly.isLoggingEnabled()) {
                    Log.i(Countly.TAG, "[Consent] Giving consent for all features");
                }

                if (_cly.isLoggingEnabled() && !_cly.isInitialized()) {
                    Log.w(Countly.TAG, "[Consent] Calling this before initialising the SDK is deprecated!");
                }

                _cly.giveConsent(_cly.validFeatureNames);
            }
        }

        /**
         * Give the consent to a feature
         *
         * @param featureNames the names of features for which consent should be given
         * @return Returns link to Countly for call chaining
         */
        public void giveConsent(String[] featureNames) {
            synchronized (_cly) {
                _cly.giveConsent(featureNames);
            }
        }

        /**
         * Set the consent of a feature
         *
         * @param featureNames feature names for which consent should be changed
         * @param isConsentGiven the consent value that should be set
         * @return Returns link to Countly for call chaining
         */
        public void setConsent(String[] featureNames, boolean isConsentGiven) {
            synchronized (_cly) {
                _cly.setConsent(featureNames, isConsentGiven);
            }
        }

        /**
         * Set the consent of a feature group
         *
         * @param groupName name of the consent group
         * @param isConsentGiven the value that should be set for this consent group
         * @return Returns link to Countly for call chaining
         */
        public void setConsentFeatureGroup(String groupName, boolean isConsentGiven) {
            synchronized (_cly) {
                _cly.setConsentFeatureGroup(groupName, isConsentGiven);
            }
        }

        /**
         * Group multiple features into a feature group
         *
         * @param groupName name of the consent group
         * @param features array of feature to be added to the consent group
         * @return Returns link to Countly for call chaining
         */
        public void createFeatureGroup(String groupName, String[] features) {
            synchronized (_cly) {
                _cly.createFeatureGroup(groupName, features);
            }
        }
    }
}
