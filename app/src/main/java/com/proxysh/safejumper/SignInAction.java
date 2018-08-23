/*
 * Copyleft (/c) MMXV, Proxy.sh
 * Distributed under the GNU GPL v2
 */
package com.proxysh.safejumper;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.proxysh.safejumper.openvpn.ConfigManager;
import com.proxysh.safejumper.service.IPChecker;

import com.proxy.sh.safejumper.R;

public class SignInAction {
	
	private EditText editUsername;
	private EditText editpasswd;
	private Button   buttonSignIn;
	private CheckBox checkRememberMe, checkAutoLaunch, checkDisablePing, checkAllDisplay;
    private Spinner spnIcsOpenVpn;
	private SignInTask asyncTask = null;
	
	private AppActivity owner;
    private ConfigManager configManager;

    public SignInAction(View v, AppActivity o) {
		
		this.owner = o;
        configManager = ConfigManager.getInstance(o);
		
		editUsername = (EditText) v.findViewById(R.id.editUsername);
		editpasswd = (EditText) v.findViewById(R.id.editPasswd);
		buttonSignIn = (Button) v.findViewById(R.id.buttonSignIn);
		checkRememberMe = (CheckBox) v.findViewById(R.id.checkSaveMe);
		checkAutoLaunch = (CheckBox) v.findViewById(R.id.checkAutoLaunch);
		checkDisablePing = (CheckBox) v.findViewById(R.id.checkDisablePing);
		checkAllDisplay = (CheckBox) v.findViewById(R.id.checkAllDisplay);
		spnIcsOpenVpn = (Spinner) v.findViewById(R.id.spnIcsOpenVpn);

		//load crendential
		boolean saveme = configManager.prefBoolForKey(ConfigManager.PK_SAVE_CREDENTIALS);
		checkRememberMe.setChecked(saveme);
		String lastuser = configManager.prefStringForKey(ConfigManager.PK_LAST_USER);
		editUsername.setText(lastuser);
		if (saveme) {
			String pwd = configManager.getUserObject(lastuser);
			editpasswd.setText(pwd != null ? pwd:"");
		};
		
		//register event listener
		checkRememberMe.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				//save to config
				configManager.setPrefBool(ConfigManager.PK_SAVE_CREDENTIALS, isChecked);
			}
		});
		
		loadPredefinedOption();
		checkAutoLaunch.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				//save to config
				ConfigManager.setAutoLaunchOnBoot(owner, isChecked);
			}
		});

		checkDisablePing.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				//save to config
				configManager.setPrefBool(ConfigManager.PK_DISABLE_PING, isChecked);
			}
		});

		checkAllDisplay.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				//save to config
				configManager.setPrefBool(ConfigManager.PK_ALL_DISPLAY, isChecked);
			}
		});

        spnIcsOpenVpn.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                configManager.setPrefInt(ConfigManager.PK_ICS_OPENVPN, i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
        spnIcsOpenVpn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if(configManager.ifExternalOpenVpnInstalled())
                    return false;
                if(event.getAction() != MotionEvent.ACTION_UP)
                    return true;
                // install ics-openvpn from Google Play
                AlertDialog.Builder builder = new AlertDialog.Builder(owner);
                builder.setMessage("Install OpenVPN for Android?");
                builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dd, int i) {
                        dd.dismiss();
                    }
                });
                builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dd, int i) {
                        dd.dismiss();
                        final String appPackageName = "de.blinkt.openvpn";
                        try {
                            owner.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
                        } catch (android.content.ActivityNotFoundException anfe) {
                            owner.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
                        }
                    }
                });
                builder.show();
                return true;
            }
        });

		buttonSignIn.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                sigin();
            }
        });
		
		if (configManager.prefBoolForKey(ConfigManager.PK_AUTO_CONNECT)) {
			if (saveme) {
				sigin();
			}
		}
	}


	
	private boolean isParamValidate() {
		
		String username = editUsername.getText().toString();
		String passwd = editpasswd.getText().toString();
		
		if (username == null || username.isEmpty())
			return false;
		
		if (passwd == null || passwd.isEmpty())
			return false;
		
		return true;
	}
	
	private void sigin() {
		if (isParamValidate()) {
			buttonSignIn.setEnabled(false);
			if (asyncTask != null)
			{
				asyncTask.cancel(true);
				asyncTask = null;
			}
			asyncTask = new SignInTask();
			asyncTask.execute();
		}
	}
	private class SignInTask extends AsyncTask<Void, Void, Boolean> {

		private ProgressDialog dialog;
		@Override 
		protected Boolean doInBackground(Void... params) {
			String user = editUsername.getText().toString();
			String pwd = editpasswd.getText().toString();
			boolean enableAllLocation = configManager.prefBoolForKey(ConfigManager.PK_ALL_DISPLAY);
			return IPChecker.getInstance(null).registerTo(user, pwd, enableAllLocation);
		}

		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);
			dialog.dismiss();
			buttonSignIn.setEnabled(true);
			if (!result.booleanValue()) {
				Toast.makeText(owner, owner.getString(R.string.invalid_credential), Toast.LENGTH_LONG).show();
				editUsername.requestFocus();
			} else {
				ConfigManager.activeUserName = editUsername.getText().toString();
				ConfigManager.activePasswdOfUser = editpasswd.getText().toString();
				if (checkRememberMe.isChecked()) {
					configManager.setPrefString(ConfigManager.PK_LAST_USER, ConfigManager.activeUserName);
					configManager.setUserObject(editUsername.getText().toString(), ConfigManager.activePasswdOfUser);
				}
				owner.onSignIn();
			}
			this.cancel(true);
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			dialog = ProgressDialog.show(owner, "", owner.getString(R.string.waiting_assist), true);
		}
	}
	public void loadPredefinedOption() {
		checkAutoLaunch.setChecked(ConfigManager.autoLaunchOnBoot(owner));
		checkDisablePing.setChecked(configManager.prefBoolForKey(ConfigManager.PK_DISABLE_PING));
		checkAllDisplay.setChecked(configManager.prefBoolForKey(ConfigManager.PK_ALL_DISPLAY));
        if(configManager.ifExternalOpenVpnInstalled()) {
            spnIcsOpenVpn.setSelection(configManager.prefIntForKey(ConfigManager.PK_ICS_OPENVPN));
//            spnIcsOpenVpn.setEnabled(true);
        }
        else {
            spnIcsOpenVpn.setSelection(ConfigManager.ICS_OPENVPN_BUILTIN);
//            spnIcsOpenVpn.setEnabled(false);
        }
	}
}
