package at.drnet.android.zramconfig;

import java.util.Timer;
import java.util.TimerTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.support.v4.app.NavUtils;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import com.stericson.RootTools.*;

public class ZRAMconfigActivity extends Activity {

	private ToggleButton startStopButton;
	private Button getEnvButton;
	private Button setDefaultsButton;
	private TextView stateInfo;
	private final int shellTimeout=60000;
	private boolean prefsOK=true;
	private final int reqCode=0x1717;
	private boolean finishOnUserLeave=false;

	//This just helps dev/debug on a non-rooted device, e.g. AVD emulator
	private final boolean rootedDevice=true;
	
	public void onUserLeaveHint () {
		if (finishOnUserLeave) finish();
	}
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        
    	super.onCreate(savedInstanceState);
    	Toast toast;
        
        //Make sure, we have defaults on our preferences
		SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		if (!rootedDevice || prefs.getString("ScriptPath", "").equals("") || prefs.getString("TotalSize", "").equals("")) {
	    	toast=Toast.makeText(getApplicationContext(), "Please review your settings before first start", Toast.LENGTH_SHORT);
	   		toast.show();
	   		startActivityForResult(new Intent(this, ZRAMconfigPreferencesActivity.class),reqCode);
	   		prefsOK=false;
		}

    	//Set up UI
    	setContentView(R.layout.activity_zramconfig);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        
        //run main part
        if (prefsOK) runMainPart();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_zramconfig, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
            case R.id.about:
            	//Show about dialog
            	AlertDialog dlg=new AlertDialog.Builder(this).create();
            	dlg.setCancelable(false);
            	dlg.setTitle("About zRAMconfig");
            	dlg.setMessage(Html.fromHtml(getResources().getString(R.string.about_text)));
            	dlg.setButton(DialogInterface.BUTTON_POSITIVE, "Dismiss", new DialogInterface.OnClickListener(){
            		@Override
            		public void onClick(final DialogInterface di, final int btn) {
            			di.dismiss();
            		}
            	});
            	dlg.show();
            	((TextView)dlg.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
            	return true;
            case R.id.preferences:
            	finishOnUserLeave=false;
            	startActivityForResult(new Intent(this, ZRAMconfigPreferencesActivity.class),reqCode);
    			return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode==reqCode) {
			//Flag preferences OK and start main part of App
			prefsOK=true;
			runMainPart();
		}
	}
	
    private void runMainPart() {
        finishOnUserLeave=true;

        //Show "waiting for root" notification 
    	Toast toast=Toast.makeText(getApplicationContext(), "Waiting for root access, please wait", Toast.LENGTH_SHORT);
   		toast.show();

   		//Schedule rest of application on a timer
   		Timer t=new Timer();
   		TimerTask tt=new TimerTask(){
        	public void run() {
        		runOnUiThread(new Runnable() {
        			public void run() {
        				//Initialize view with disabled buttons
        				initViewObjects();
        				disableButtons();
        				
        				//Wait for root
        				if (waitForRoot()) { 
        					//Success: start by refreshing state info
        					enableButtons();
        					getEnvButton.performClick();
        				}
        			}
        		});
        	}
   		};
   		t.schedule(tt, 250);
    }

    private boolean waitForRoot() {
        //Make sure, we have root - try this 10 times, with a second delay in between
    	RootTools.debugMode=true;
        int i=0;
        while (true) {
            try {
            	RootTools.getShell(rootedDevice);
            	break;
            } catch (Exception e) {
            	if (i++<10) {
            		//Sleep
            		try {
            			for (int slp=0; slp<=1000; slp+=10) Thread.sleep(10);
            		} catch (InterruptedException ee) { }
            		//And retry
            		continue;
            	} else {
            		//Give up: Show a message box and exit
                	AlertDialog dlg=new AlertDialog.Builder(this).create();
                	dlg.setCancelable(false);
                	dlg.setTitle("Root needed");
                	dlg.setMessage("zRAMconfig needs root access, but your device does not provide it. Aborting now.");
                	dlg.setButton(DialogInterface.BUTTON_POSITIVE, "Abort", new DialogInterface.OnClickListener(){
                		@Override
                		public void onClick(final DialogInterface di, final int btn) {
                			di.dismiss();
                			android.os.Process.killProcess(android.os.Process.myPid());
                		}
                	});
                	dlg.show();
                	return false;
            	}
            }
        }
    	return true;
    }
    
    private void initViewObjects() {
        //Prepare state view
        stateInfo=(TextView)findViewById(R.id.stateInfo);
        
        //Prepare start-stop button
        startStopButton=(ToggleButton)findViewById(R.id.startStopButton);
        startStopButton.setOnClickListener(new OnClickListener() {
        	@Override
        	public void onClick(View v) {
        		onStartStopClicked();
        	}
        });
        
        //Prepare refresh button
        getEnvButton=(Button)findViewById(R.id.getEnvButton);
        getEnvButton.setOnClickListener(new OnClickListener() {
        	@Override
        	public void onClick(View v) {
        		getEnvClicked();
        	}
        });

        //Prepare set defaults button
        setDefaultsButton=(Button)findViewById(R.id.setDefaultsButton);
        setDefaultsButton.setOnClickListener(new OnClickListener() {
        	@Override
        	public void onClick(View v) {
        		setDefaultsClicked();
        	}
        });
    }
    
    private String runCommand(String cmd, boolean asRoot) {
    	final StringBuilder result = new StringBuilder();
   		Command command = new Command(0, cmd)
   		{
   		        @Override
   		        public void output(int id, String line)
   		        {
   		            synchronized(result) {
   		            	result.append(line+"\n");
   		            }
   		        }
   		};
   		try {
			RootTools.getShell(asRoot).add(command).waitForFinish(shellTimeout);
		} catch (Exception e) {
			return null;
		}
   		return result.toString();
    }

    private void disableButtons() {
    	startStopButton.setEnabled(false);
    	startStopButton.setClickable(false);
    	getEnvButton.setEnabled(false);
    	getEnvButton.setClickable(false);
    	setDefaultsButton.setEnabled(false);
    	setDefaultsButton.setClickable(false);
    }
    
    private void enableButtons() {
    	startStopButton.setEnabled(true);
    	startStopButton.setClickable(true);
    	getEnvButton.setEnabled(true);
    	getEnvButton.setClickable(true);
    	setDefaultsButton.setEnabled(true);
    	setDefaultsButton.setClickable(true);
    }
    
    
    private String[] getResultParams(String raw, String cmd, int paramCount) {
    	String[] result=new String[paramCount+2];
    	int pos;
    	for (pos=0;pos<=paramCount;pos++) result[pos]="";
    	raw=raw.trim();
    	
    	//First line must start with "##"
    	result[paramCount+1]="Could not parse result: No command line";
    	pos=raw.indexOf("\n");
    	if (pos<4) return result;
    	String line=raw.substring(0,pos);
    	raw=raw.substring(pos+1);
    	String match=line.substring(0,2);
    	if (!match.equals("##")) return result;
    	result[paramCount]=line.substring(3);
    	
    	//Next line must be "** CMD: Start"
    	result[paramCount+1]="Could not parse result: Missing start marker";
    	pos=raw.indexOf("\n");
    	if (pos<8) return result;
    	line=raw.substring(0,pos).toUpperCase();
    	raw=raw.substring(pos+1);
    	match="** "+cmd.toUpperCase()+": START";
    	if (!line.equals(match)) return result;

    	//Last line must be "** CMD: [result]"
    	result[paramCount+1]="Could not parse result: Missing End marker";
    	pos=raw.lastIndexOf("\n");
    	if (pos<=0) {
    		line=raw.trim();
    		raw="";
    	} else {
        	line=raw.substring(pos+1);
        	raw=raw.substring(0,pos);
    	}
    	pos=5+cmd.length();
    	if (line.length()<pos) return result;
    	match="** "+cmd.toUpperCase()+": ";
    	if (!line.substring(0, pos).toUpperCase().equals(match)) return result;
    	result[paramCount+1]=line.substring(pos).trim().toUpperCase();

    	//Now the rest of the string
    	while (raw.length()>0) {
        	pos=raw.indexOf("\n");
    		if (pos<0) pos=raw.length();
    		if (pos>0) {
    			line=raw.substring(0, pos);
    			if (line.substring(0,1).equals("[")) {
    				int bpos=line.indexOf("]");
    				if (bpos>0) {
    					match=line.substring(1,bpos);
    					int idx=parseInt(match,-1);
    					if ((idx>=0) && (idx<paramCount)) {
    						bpos=line.indexOf(":", bpos+1);
    						if (bpos>0) result[idx]=line.substring(bpos+1).trim();
    					}
    				}
    			}
    		}
    		if (pos>=raw.length()) break;
    		raw=raw.substring(pos+1);
    	}
    	return result;
    }
    
    private int parseInt(String s, int defaultval) {
    	int i=defaultval;
    	try { i=Integer.parseInt(s); }
    	catch (Exception e) { }
    	return i;
    }
    
    private void onStartStopClicked() {
    	//Disable buttons
    	disableButtons();
    	
    	//Handle the click
    	Context ctx=getApplicationContext();
    	if (startStopButton.isChecked()) {
    		//Show notification first
    		Toast toast=Toast.makeText(ctx, "Trying to enable zRAM swap", Toast.LENGTH_SHORT);
    		toast.show();
    		
       		//Run the work on a background thread
       		stateInfo.setText("Enabling zRAM swap, please wait ...");
       		doStartZRAM();
    	} else {
    		//Show notification first
    		Toast toast=Toast.makeText(ctx, "Trying to disable zRAM swap", Toast.LENGTH_SHORT);
    		toast.show();

    		//Run the work on a background thread
       		stateInfo.setText("Disabling zRAM swap, this can take some time, please wait ...");
       		doStopZRAM();
    	}
    }

    private void getEnvClicked() {
    	//Disable buttons
    	disableButtons();
    	
    	//Handle the click; SHow notification first
    	Context ctx=getApplicationContext();
   		Toast toast=Toast.makeText(ctx, "Trying to read zRAM swap information", Toast.LENGTH_SHORT);
   		toast.show();

   		//Run the work on a background thread
   		stateInfo.setText("Reading zRAM swap state information, please wait ...");
   		doGetEnv();
    }
    
    private void setDefaultsClicked() {
    	//Disable buttons
    	disableButtons();
    	
    	//Handle the click; Show notification first
    	Context ctx=getApplicationContext();
   		Toast toast=Toast.makeText(ctx, "Trying to set zRAM autostart preferences", Toast.LENGTH_SHORT);
   		toast.show();

   		//Run the work on a background thread
   		stateInfo.setText("Setting zRAM autostart preferences, please wait ...");
   		doSetDefaults();
    }
    
    private void doStopZRAM() {
    	stateInfo.setText("Stopping zRAM, this can take up to a minute.\nPlease be patient!");
    	TimerTask tt=new TimerTask() {
    		public void run() {
    			//Give display a chance to update
    			try { Thread.sleep(200); }
				catch (InterruptedException e) { }
    			
    			//Off we go ...
    			SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    			String cmd=prefs.getString("ScriptPath", "");
    			cmd+=" stop ";
    			cmd+=prefs.getString("SwappinessOff", "");
    			String res=runCommand(cmd, rootedDevice);
    			if (res!=null) res="##"+cmd+"\n"+res.trim();
    			stopZRAMdone(res);
    		}
    	};
    	Timer t=new Timer();
    	t.schedule(tt, 200);
    }
    
    private void stopZRAMdone(final String result) {
		runOnUiThread(new Runnable() {
			public void run() {
				stopZRAMresult(result);
			}
		});
    }
    
    private void stopZRAMresult(String result) {
    	Toast toast;
    	String text="";
    	if (result==null) {
			startStopButton.setChecked(true);
    		stateInfo.setText("Error stopping zRAM swap");
        	toast=Toast.makeText(getApplicationContext(), "Error stopping zRAM swap", Toast.LENGTH_SHORT);
       		toast.show();
    	} else {
    		String[] parsed=getResultParams(result, "STOP", 1);
    		if (parsed[2].toUpperCase().equals("SUCCESS")) {
				startStopButton.setChecked(false);
				text="zRAM stopped successfully";
				int tmp=parseInt(parsed[0],0);
				text+="\nKernel swappiness is set to "+tmp;
    		} else {
    			startStopButton.setChecked(true);
    			toast=Toast.makeText(getApplicationContext(), "Error stopping zRAM swap: "+parsed[2], Toast.LENGTH_SHORT);
    			toast.show();
    			text="Error stopping zRAM swap:\n"+parsed[2];
    		}
    		
    		text+="\n\n\n==========================================\nRaw message exchange:\n"+result+"\n==========================================";
    		stateInfo.setText((CharSequence)text);
    	}
    	enableButtons();
    }
    
    private void doStartZRAM() {
    	stateInfo.setText("Starting zRAM, this can take some seconds.\nPlease be patient!");
    	TimerTask tt=new TimerTask() {
    		public void run() {
    			//Give display a chance to update
    			try { Thread.sleep(200); }
				catch (InterruptedException e) { }
    			
    			//Off we go ...
    			SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    			String cmd=prefs.getString("ScriptPath", "");
    			cmd+=" start ";
    			cmd+=prefs.getString("DeviceCount", "");
    			cmd+=" ";
    			cmd+=prefs.getString("TotalSize", "");
    			cmd+=" ";
    			cmd+=prefs.getString("SwappinessOn", "");
    			String res=runCommand(cmd, rootedDevice);
    			if (res!=null) res="##"+cmd+"\n"+res.trim();
    			startZRAMdone(res);
    		}
    	};
    	Timer t=new Timer();
    	t.schedule(tt, 200);
    }
        
    private void startZRAMdone(final String result) {
		runOnUiThread(new Runnable() {
			public void run() {
				startZRAMresult(result);
			}
		});
    }
   
    private void startZRAMresult(String result) {
    	Toast toast;
    	String text="";
    	if (result==null) {
			startStopButton.setChecked(false);
    		stateInfo.setText("Error starting zRAM swap");
        	toast=Toast.makeText(getApplicationContext(), "Error starting zRAM swap", Toast.LENGTH_SHORT);
       		toast.show();
    	} else {
    		String[] parsed=getResultParams(result, "START", 3);
    		if (parsed[4].toUpperCase().equals("SUCCESS")) {
				startStopButton.setChecked(true);
				text="zRAM started successfully";
				int dev=parseInt(parsed[0],0);
				if (dev<=0) {
					text+=", but is inactive\nCheck your configuration!";
				} else {
					text+="\n"+dev+" instances active";
					int tmp=parseInt(parsed[1],0);
					if (tmp>0) {
						text+="\nMax. "+tmp+" KB ("+((tmp+512)/1024)+" MB) used for zRAM";
						tmp=parseInt(parsed[2],0);
						text+="\nKernel swappiness is set to "+tmp;
					} else  {
						text+="\nNo memory will be used for zRAM\nCheck your configuration";
					}
				}
    		} else {
    			startStopButton.setChecked(false);
    			toast=Toast.makeText(getApplicationContext(), "Error starting zRAM swap: "+parsed[4], Toast.LENGTH_SHORT);
    			toast.show();
    			text="Error starting zRAM swap:\n"+parsed[4];
    		}
    		
    		text+="\n\n\n==========================================\nRaw message exchange:\n"+result+"\n==========================================";
    		stateInfo.setText((CharSequence)text);
    	}
    	enableButtons();
    }
    
    private void doGetEnv() {
    	stateInfo.setText("Reading zRAM state, this can take some seconds.\nPlease be patient!");
    	TimerTask tt=new TimerTask() {
    		public void run() {
    			//Give display a chance to update
    			try { Thread.sleep(200); }
				catch (InterruptedException e) { }
    			
    			//Off we go ...
    			SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    			String cmd=prefs.getString("ScriptPath", "");
    			cmd+=" env";
    			String res=runCommand(cmd, rootedDevice);
    			if (res!=null) res="##"+cmd+"\n"+res.trim();
    			getEnvDone(res);
    		}
    	};
    	Timer t=new Timer();
    	t.schedule(tt, 200);
    }
    
    private void getEnvDone(final String result) {
		runOnUiThread(new Runnable() {
			public void run() {
				getEnvResult(result);
			}
		});
    }
    
    private void getEnvResult(String result) {
    	Toast toast;
    	String text="";
    	if (result==null) {
    		stateInfo.setText("Error reading zRAM swap state");
        	toast=Toast.makeText(getApplicationContext(), "Error reading zRAM swap state", Toast.LENGTH_SHORT);
       		toast.show();
    	} else {
    		String[] parsed=getResultParams(result, "ENV", 9);
    		if (parsed[10].toUpperCase().equals("SUCCESS")) {
    			if (parseInt(parsed[0],0)==1) {
    				text="zRAM is loaded";
    				int dev=parseInt(parsed[1],0);
    				if (dev<=0) {
    					text+=", but is inactive";
    					startStopButton.setChecked(false);
    				} else {
    					text+=" with "+dev+" active instances";
    					//Calculate memory usage
    					int mem=0;
    					String[] s=parsed[7].split("\\s+");
    					for(int i=0;i<s.length;i++) mem+=parseInt(s[i],0);
    					text+="\n"+((mem+512)/1024)+" out of "+parsed[3]+" KB used for zRAM";
    					if (mem>0) {
    						//Calculate payload
    						int pl=0;
    						s=parsed[8].split("\\s+");
    						for(int i=0;i<s.length;i++) pl+=parseInt(s[i],0);
    						text+="\n"+((pl+512)/1024)+" KB payload in zRAM";
    						text+="\nCompression factor is "+((long)pl*100L/(long)mem)+"%\nRAM balance is ";
    						if (pl>mem) text+="+";
    						text+=((pl-mem+512)/1024)+" KB (";
    						if (pl>mem) text+="+";
    						text+=((((pl-mem+512)/1024)+512)/1024)+" MB)";
    						//Mark active
    						startStopButton.setChecked(true);
    					} else {
    						startStopButton.setChecked(false);
    					}
    				}
    			} else {
    				text="zRAM is not loaded";
    				startStopButton.setChecked(false);
    			}
    		} else {
    			toast=Toast.makeText(getApplicationContext(), "Error reading zRAM swap state: "+parsed[10], Toast.LENGTH_SHORT);
    			toast.show();
    			text="Error reading zRAM swap state:\n"+parsed[10];
    		}
    		
    		text+="\n\n\n==========================================\nRaw message exchange:\n"+result+"\n==========================================";
    		stateInfo.setText((CharSequence)text);
    	}
    	enableButtons();
    }

    private void doSetDefaults() {
    	stateInfo.setText("Setting zRAM autostart preferences, this can take some seconds.\nPlease be patient!");
    	TimerTask tt=new TimerTask() {
    		public void run() {
    			//Give display a chance to update
    			try { Thread.sleep(200); }
				catch (InterruptedException e) { }
    			
    			//Off we go ...
    			SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    			String cmd=prefs.getString("ScriptPath", "");
    			cmd+=" setdefaults";
    			String res=runCommand(cmd, rootedDevice);
    			if (res!=null) res="##"+cmd+"\n"+res.trim();
    			setDefaultsDone(res);
    		}
    	};
    	Timer t=new Timer();
    	t.schedule(tt, 200);
    }
    
    private void setDefaultsDone(final String result) {
		runOnUiThread(new Runnable() {
			public void run() {
				setDefaultsResult(result);
			}
		});
    }
    
    private void setDefaultsResult(String result) {
    	Toast toast;
    	String text="";
    	if (result==null) {
    		stateInfo.setText("Error setting zRAM autostart preferences");
        	toast=Toast.makeText(getApplicationContext(), "Error setting zRAM autostart preferences", Toast.LENGTH_SHORT);
       		toast.show();
    	} else {
    		String[] parsed=getResultParams(result, "SETDEFAULTS", 0);
    		if (parsed[1].toUpperCase().equals("SUCCESS")) {
    			text="Successfully set zRAM autostart preferences to current state";
    		} else {
    			toast=Toast.makeText(getApplicationContext(), "Error setting zRAM autostart preferences: "+parsed[1], Toast.LENGTH_SHORT);
    			toast.show();
    			text="Error setting zRAM autostart preferences:\n"+parsed[1];
    		}
    		
    		text+="\n\n\n==========================================\nRaw message exchange:\n"+result+"\n==========================================";
    		stateInfo.setText((CharSequence)text);
    	}
    	enableButtons();
    }
}
