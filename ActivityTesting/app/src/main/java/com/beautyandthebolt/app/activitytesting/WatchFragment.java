package com.beautyandthebolt.app.activitytesting;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

import java.util.List;

public class WatchFragment extends Fragment {
    private VideoView videoView;

    private int numSecretTaps = 0;
    public int numSecretTapsWarn = 5;
    public int numSecretTapsTrigger = 10;
    private long timeFirstSecretTap;
    private Toast secretToast;
    public int currentVideoResource = R.raw.ultimaker;


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_watch, null);
        setupSecretButton();

        SharedPreferences settings = getActivity().getApplicationContext().getSharedPreferences("SystemState", 0);
        currentTimeVideo = settings.getInt("currentTimeVideo", 0);
        currentVideoResource = settings.getInt("currentVideoResource", R.raw.ultimaker);

        videoView = (VideoView) view.findViewById(R.id.videoView);

        Log.d("video", "Video View Created. Current Time: " + currentTimeVideo);

        startVideo(currentVideoResource,currentTimeVideo);

        return view;
    }

    public int currentTimeVideo = 0;

    public int GetVideoTime(){
        return currentTimeVideo;
    }
    @Override
    public void onDestroyView() {
        if(videoView.getCurrentPosition() !=0) {
            currentTimeVideo = videoView.getCurrentPosition();
        }
        Log.d("video", "Video View Destroyed. Current Time: "+currentTimeVideo);
        SharedPreferences settings = getActivity().getApplicationContext().getSharedPreferences("SystemState", 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt("currentTimeVideo", currentTimeVideo);
        editor.putInt("currentVideoResource", currentVideoResource);
        editor.apply();
        super.onDestroyView();
    }

    public void startVideo(int videoResourceID, int seek){
        currentVideoResource = videoResourceID;
        videoView.stopPlayback();
        Uri uri = Uri.parse("android.resource://"+getActivity().getPackageName()+"/"+videoResourceID);
        videoView.setVideoURI(uri);
        videoView.start();
        videoView.seekTo(seek);
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {


            @Override
            public void onPrepared(MediaPlayer mp) {
                Log.d("video", "Looping is turned on!!");
                mp.setLooping(true);
                mp.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                    @Override
                    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                        MediaController controller = new MediaController(getActivity());
                        videoView.setMediaController(controller);
                        controller.setAnchorView(videoView);
                    }
                });
            }
        });
    }

    DevicePolicyManager dpm;
    ComponentName mAdminComponentName;

    public void setupSecretButton(){
        Button secretButton = (Button) getActivity().findViewById(R.id.secretButton);
        secretButton.setVisibility(View.VISIBLE);
        secretButton.setBackgroundColor(Color.TRANSPARENT);
        secretButton.setTextColor(Color.TRANSPARENT);
        secretButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if(numSecretTaps == 0) {
                    numSecretTaps++;
                    timeFirstSecretTap = System.currentTimeMillis();
                }
                else{
                    numSecretTaps++;
                    Log.d("secretButton", "tapCountIncreased");

                    if(numSecretTaps >= numSecretTapsWarn && numSecretTaps < numSecretTapsTrigger) {
                        if(secretToast != null){
                            secretToast.cancel();
                        }
                        secretToast = Toast.makeText(getActivity(), numSecretTapsTrigger - numSecretTaps + " taps until setup menu.",Toast.LENGTH_SHORT);
                        secretToast.setMargin(50,50);
                        secretToast.show();
                    }
                    if(numSecretTaps >= numSecretTapsTrigger){
                        numSecretTaps = 0;
                        if(secretToast != null){
                            secretToast.cancel();
                        }
                        secretToast= Toast.makeText(getActivity(), " Entering Setup Mode",Toast.LENGTH_SHORT);
                        secretToast.setMargin(50,50);
                        secretToast.show();

                        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                        builder.setTitle("Choose Video")
                                .setItems(R.array.video_titles ,new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        Log.d("secretButton", "Video Selected: "+ which);
                                        TypedArray ta = getResources().obtainTypedArray(R.array.video_uris);
                                        int videoResourceID = ta.getResourceId(which, 0);
                                        currentTimeVideo = 0;
                                        startVideo(videoResourceID, 0);
                                    }
                                });
                        builder.setPositiveButton("Lock App", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // First, confirm that this package is whitelisted to run in lock task mode.
//                                Context context = getActivity();
//
//                                mAdminComponentName = MyDeviceAdminReceiver.getComponentName(getActivity());
//                                dpm = (DevicePolicyManager) getActivity().getSystemService(Context.DEVICE_POLICY_SERVICE);
                                getActivity().startLockTask();

//                                List<ComponentName> admins = dpm.getActiveAdmins();
//                                for(ComponentName admin: admins){
//                                    Log.d("lock", "Admin: "+admin.flattenToShortString());
//                                }
//
//                                if (dpm.isAdminActive(mAdminComponentName))
//                                {
//                                    Log.d("lock", "application is admin");
//
//                                    if (dpm.isDeviceOwnerApp(getActivity().getPackageName()))
//                                    {
//                                        Log.d("lock", "application is admin");
//                                    }
//                                }
//                                else{
//                                    Log.d("lock", "application is not admin");
//                                }

//                                if (dpm.isLockTaskPermitted(context.getPackageName())) {
//                                    getActivity().startLockTask();
//                                } else {
//                                    String[] APP_PACKAGES = {getActivity().getPackageName()};
//                                    Log.d("lock", "Setting lockable packages: " + APP_PACKAGES[0]);
//                                    dpm.setLockTaskPackages(mAdminComponentName, APP_PACKAGES);
//                                    Log.d("lock", "App package is now lockable;");
//                                    getActivity().startLockTask();
//                                }

//                                if(dpm.isAdminActive(mAdminComponentName)) {
//                                    if (dpm.isLockTaskPermitted(context.getPackageName())) {
//                                        getActivity().startLockTask();
//                                    } else {
//                                        String[] APP_PACKAGES = {getActivity().getPackageName()};
//                                        Log.d("lock", "Setting lockable packages: " + APP_PACKAGES[0]);
//                                        dpm.setLockTaskPackages(mAdminComponentName, APP_PACKAGES);
//                                        Log.d("lock", "App package is now lockable;");
//                                        getActivity().startLockTask();
//                                    }
//                                }
//                                else{
//                                    Log.d("lock", "application is not admin");
//                                }
                            }
                        });
                        builder.setNeutralButton("Unlock App", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                getActivity().stopLockTask();
                            }
                        });
                        AlertDialog dialog = builder.create();
                        dialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE );
                        dialog.show();
                        dialog.getWindow().getDecorView().setSystemUiVisibility(getActivity().getWindow().getDecorView().getSystemUiVisibility());
                        dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
                    }
                }
            }
        });
    }

}
