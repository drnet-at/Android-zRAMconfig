package at.drnet.android.zramconfig;

import at.drnet.android.zramconfig.R;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class ZRAMconfigPreferencesActivity extends PreferenceActivity {
    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	addPreferencesFromResource(R.xml.preferences);
    	PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
    }
}
