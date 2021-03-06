package mega.privacy.android.app;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

import mega.privacy.android.app.lollipop.listeners.MultipleAttachChatListener;
import mega.privacy.android.app.lollipop.megachat.calls.ChatCallActivity;
import mega.privacy.android.app.snackbarListeners.SnackbarNavigateOption;
import nz.mega.sdk.MegaApiAndroid;
import nz.mega.sdk.MegaChatApiAndroid;
import nz.mega.sdk.MegaChatRoom;

import static mega.privacy.android.app.utils.LogUtil.*;
import static mega.privacy.android.app.utils.Util.*;
import static mega.privacy.android.app.utils.DBUtil.*;
import static mega.privacy.android.app.utils.Constants.*;

public class BaseActivity extends AppCompatActivity {

    private MegaApiAndroid megaApi;
    private MegaChatApiAndroid megaChatApi;
    private MegaApiAndroid megaApiFolder;

    private AlertDialog sslErrorDialog;

    boolean delaySignalPresence = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        logDebug("onCreate");

        super.onCreate(savedInstanceState);
        checkMegaApiObjects();

        LocalBroadcastManager.getInstance(this).registerReceiver(sslErrorReceiver,
                new IntentFilter(BROADCAST_ACTION_INTENT_SSL_VERIFICATION_FAILED));

        LocalBroadcastManager.getInstance(this).registerReceiver(signalPresenceReceiver,
                new IntentFilter(BROADCAST_ACTION_INTENT_SIGNAL_PRESENCE));
    }

    @Override
    protected void onPause() {
        logDebug("onPause");

        checkMegaApiObjects();
        super.onPause();
    }

    @Override
    protected void onResume() {
        logDebug("onResume");

        super.onResume();
        setAppFontSize(this);

        checkMegaApiObjects();

        retryConnectionsAndSignalPresence();
    }

    @Override
    protected void onDestroy() {
        logDebug("onDestroy");

        LocalBroadcastManager.getInstance(this).unregisterReceiver(sslErrorReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(signalPresenceReceiver);

        super.onDestroy();
    }

    /**
     * Method to check if exist all required MegaApiAndroid and MegaChatApiAndroid objects
     * or create them if necessary.
     */
    private void checkMegaApiObjects() {
        logDebug("checkMegaApiObjects");

        if (megaApi == null){
            megaApi = ((MegaApplication)getApplication()).getMegaApi();
        }

        if (megaApiFolder == null) {
            megaApiFolder = ((MegaApplication) getApplication()).getMegaApiFolder();
        }

        if(isChatEnabled()){
            if (megaChatApi == null){
                megaChatApi = ((MegaApplication)getApplication()).getMegaChatApi();
            }
        }
    }

    /**
     * Broadcast receiver to manage a possible SSL verification error.
     */
    private BroadcastReceiver sslErrorReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                logDebug("BROADCAST TO MANAGE A SSL VERIFICATION ERROR");
                if (sslErrorDialog != null && sslErrorDialog.isShowing()) return;
                showSSLErrorDialog();
            }
        }
    };

    /**
     * Broadcast to send presence after first launch of app
     */
    private BroadcastReceiver signalPresenceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                logDebug("BROADCAST TO SEND SIGNAL PRESENCE");
                if(delaySignalPresence && megaChatApi != null && megaChatApi.getPresenceConfig() != null && megaChatApi.getPresenceConfig().isPending()==false){
                    delaySignalPresence = false;
                    retryConnectionsAndSignalPresence();
                }
            }
        }
    };

    /**
     * Method to display an alert dialog indicating that the MEGA SSL key
     * can't be verified (API_ESSL Error) and giving the user several options.
     */
    private void showSSLErrorDialog(){

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View v = inflater.inflate(R.layout.dialog_three_vertical_buttons, null);
        builder.setView(v);

        TextView title = v.findViewById(R.id.dialog_title);
        TextView text = v.findViewById(R.id.dialog_text);

        Button retryButton = v.findViewById(R.id.dialog_first_button);
        Button openBrowserButton = v.findViewById(R.id.dialog_second_button);
        Button dismissButton = v.findViewById(R.id.dialog_third_button);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.RIGHT;

        title.setText(R.string.ssl_error_dialog_title);
        text.setText(R.string.ssl_error_dialog_text);
        retryButton.setText(R.string.general_retry);
        openBrowserButton.setText(R.string.general_open_browser);
        dismissButton.setText(R.string.general_dismiss);

        sslErrorDialog = builder.create();
        sslErrorDialog.setCancelable(false);
        sslErrorDialog.setCanceledOnTouchOutside(false);

        retryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sslErrorDialog.dismiss();
                megaApi.reconnect();
                megaApiFolder.reconnect();
            }
        });

        openBrowserButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sslErrorDialog.dismiss();
                Uri uriUrl = Uri.parse("https://mega.nz/");
                Intent launchBrowser = new Intent(Intent.ACTION_VIEW, uriUrl);
                startActivity(launchBrowser);
            }
        });

        dismissButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sslErrorDialog.dismiss();
                megaApi.setPublicKeyPinning(false);
                megaApi.reconnect();
                megaApiFolder.setPublicKeyPinning(false);
                megaApiFolder.reconnect();
            }
        });

        sslErrorDialog.show();
    }

    protected void retryConnectionsAndSignalPresence(){
        logDebug("retryConnectionsAndSignalPresence");
        try{
            if (megaApi != null){
                megaApi.retryPendingConnections();
            }

            if(isChatEnabled()) {
                if (megaChatApi != null) {
                    megaChatApi.retryPendingConnections(false, null);

                    if(megaChatApi.getPresenceConfig() != null && megaChatApi.getPresenceConfig().isPending() == false){
                        delaySignalPresence = false;
                        if(!(this instanceof ChatCallActivity) && megaChatApi.isSignalActivityRequired()){
                            logDebug("Send signal presence");
                            megaChatApi.signalPresenceActivity();
                        }
                    }
                    else {
                        delaySignalPresence = true;
                    }
                }
            }
        }
        catch (Exception e){
            logWarning("Exception", e);
        }
    }

    @Override
    public void onBackPressed() {
        retryConnectionsAndSignalPresence();
        super.onBackPressed();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN ){
            retryConnectionsAndSignalPresence();
        }
        return super.dispatchTouchEvent(event);
    }

    /**
     * Method to display a simple Snackbar.
     *
     * @param view Layout where the snackbar is going to show.
     * @param s Text to shown in the snackbar
     */
    public void showSnackbar (View view, String s) {
        showSnackbar(SNACKBAR_TYPE, view, s, -1);
    }

    /**
     * Method to display a simple or action Snackbar.
     *
     * @param type There are three possible values to this param:
     *            - SNACKBAR_TYPE: creates a simple snackbar
     *            - MESSAGE_SNACKBAR_TYPE: creates an action snackbar which function is to go to Chat section
     *            - NOT_SPACE_SNACKBAR_TYPE: creates an action snackbar which function is to go to Storage-Settings section
     * @param view Layout where the snackbar is going to show.
     * @param s Text to shown in the snackbar
     */
    public void showSnackbar (int type, View view, String s) {
        showSnackbar(type, view, s, -1);
    }

    /**
     * Method to display a simple or action Snackbar.
     *
     * @param type There are three possible values to this param:
     *            - SNACKBAR_TYPE: creates a simple snackbar
     *            - MESSAGE_SNACKBAR_TYPE: creates an action snackbar which function is to go to Chat section
     *            - NOT_SPACE_SNACKBAR_TYPE: creates an action snackbar which function is to go to Storage-Settings section
     * @param view Layout where the snackbar is going to show.
     * @param s Text to shown in the snackbar
     * @param idChat Chat ID. If this param has a valid value, different to -1, the function of MESSAGE_SNACKBAR_TYPE ends in the specified chat
     */
    public void showSnackbar (int type, View view, String s, long idChat) {
        logDebug(("showSnackbar: " + s));
        Display  display = getWindowManager().getDefaultDisplay();
        DisplayMetrics outMetrics = new DisplayMetrics();
        display.getMetrics(outMetrics);

        Snackbar snackbar = null;
        if (type == MESSAGE_SNACKBAR_TYPE && (s==null || s.isEmpty())) {
            snackbar = Snackbar.make(view, R.string.sent_as_message, Snackbar.LENGTH_LONG);
        }
        else if (type == NOT_SPACE_SNACKBAR_TYPE) {
            snackbar = Snackbar.make(view, R.string.error_not_enough_free_space, Snackbar.LENGTH_LONG);
        }
        else {
            snackbar = Snackbar.make(view, s, Snackbar.LENGTH_LONG);
        }

        Snackbar.SnackbarLayout snackbarLayout = (Snackbar.SnackbarLayout) snackbar.getView();
        snackbarLayout.setBackground(ContextCompat.getDrawable(this, R.drawable.background_snackbar));

        if (snackbarLayout.getLayoutParams() instanceof CoordinatorLayout.LayoutParams) {
            final CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) snackbarLayout.getLayoutParams();
            params.setMargins(px2dp(8, outMetrics),0,px2dp(8, outMetrics), px2dp(8, outMetrics));
            snackbarLayout.setLayoutParams(params);
        }
        else if (snackbarLayout.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) snackbarLayout.getLayoutParams();
            params.setMargins(px2dp(8, outMetrics),0, px2dp(8, outMetrics), px2dp(8, outMetrics));
            snackbarLayout.setLayoutParams(params);
        }

        switch (type) {
            case SNACKBAR_TYPE: {
                TextView snackbarTextView = (TextView)snackbar.getView().findViewById(android.support.design.R.id.snackbar_text);
                snackbarTextView.setMaxLines(5);
                snackbar.show();
                break;
            }
            case MESSAGE_SNACKBAR_TYPE: {
                snackbar.setAction("SEE", new SnackbarNavigateOption(view.getContext(), idChat));
                snackbar.show();
                break;
            }
            case NOT_SPACE_SNACKBAR_TYPE: {
                snackbar.setAction("Settings", new SnackbarNavigateOption(view.getContext()));
                snackbar.show();
                break;
            }
        }
    }

    /**
     * Method to display a simple Snackbar.
     *
     * @param context Context of the Activity where the snackbar has to be displayed
     * @param outMetrics DisplayMetrics of the current device
     * @param view Layout where the snackbar is going to show.
     * @param s Text to shown in the snackbar
     */
    public static void showSimpleSnackbar(Context context, DisplayMetrics outMetrics, View view, String s) {
        Snackbar snackbar = Snackbar.make(view, s, Snackbar.LENGTH_LONG);
        Snackbar.SnackbarLayout snackbarLayout = (Snackbar.SnackbarLayout) snackbar.getView();
        snackbarLayout.setBackground(ContextCompat.getDrawable(context, R.drawable.background_snackbar));
        final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) snackbarLayout.getLayoutParams();
        params.setMargins(px2dp(8, outMetrics),0,px2dp(8, outMetrics), px2dp(8, outMetrics));
        snackbarLayout.setLayoutParams(params);
        TextView snackbarTextView = (TextView) snackbar.getView().findViewById(android.support.design.R.id.snackbar_text);
        snackbarTextView.setMaxLines(5);
        snackbar.show();
    }

    /**
     * Method for send a file into one or more chats
     *
     * @param context Context of the Activity where the file has to be sent
     * @param chats Chats where the file has to be sent
     * @param fileHandle Handle of the file that has to be sent
     */
    public void sendFileToChatsFromContacts(Context context, ArrayList<MegaChatRoom> chats, long fileHandle){
        logDebug("sendFileToChatsFromContacts");

        MultipleAttachChatListener listener = null;

        if(chats.size()==1){
            listener = new MultipleAttachChatListener(context, chats.get(0).getChatId(), false, chats.size());
            megaChatApi.attachNode(chats.get(0).getChatId(), fileHandle, listener);
        }
        else{
            listener = new MultipleAttachChatListener(context, -1, false, chats.size());
            for(int i=0;i<chats.size();i++){
                megaChatApi.attachNode(chats.get(i).getChatId(), fileHandle, listener);
            }
        }
    }

    /**
     * Method to refresh the account details info if necessary.
     */
    protected void refreshAccountInfo(){
        logDebug("refreshAccountInfo");

        //Check if the call is recently
        logDebug("Check the last call to getAccountDetails");
        if(callToAccountDetails(getApplicationContext())){
            logDebug("megaApi.getAccountDetails SEND");
            ((MegaApplication) getApplication()).askForAccountDetails();
        }
    }
}
