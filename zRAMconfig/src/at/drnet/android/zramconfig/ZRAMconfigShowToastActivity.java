package at.drnet.android.zramconfig;

import android.os.Bundle;
import android.widget.Toast;
import android.app.Activity;
import android.content.Intent;

public class ZRAMconfigShowToastActivity extends Activity {
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Intent intent = getIntent();
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		String message=intent.getStringExtra("message");
		if (message.length()==0) message="No text for Toast given";
		Toast toast=Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG);
		toast.show();
		finish();
	}
}
