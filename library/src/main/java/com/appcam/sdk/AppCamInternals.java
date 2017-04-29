package com.appcam.sdk;

import android.app.Activity;
import android.app.Application;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PersistableBundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.appcam.sdk.appstate.AppStateListener;
import com.appcam.sdk.appstate.AppStateMonitor;

import java.io.File;
import java.io.IOException;

import static com.appcam.sdk.AppCam.QUALITY_LOW;
import static com.appcam.sdk.AppCam.QUALITY_MEDIUM;


/**
 * Created by jackunderwood on 14/04/2017.
 */

 class AppCamInternals {

    private  MediaRecorder mediaRecorder;
    private  Activity activity;
    private  MediaProjectionManager mediaProjectionManager;
    private  MediaProjection mediaProjection;
    private  String apiKey;
    private  String fileLocation;
    private  int quality;

    private boolean hasStopped = false;

    public static final int JOB_ID = 1000119;
    public static String APP_CAM_LOG = "AppCam";

    private ImageView touchView;
    private int touchSize;
    private Interpolator interpolator;

    private int videoWidth;
    private int videoHeight;
    private boolean instantUpload;

    private boolean hasInit = false;

    private boolean isRecording = false;

    private Application application;

    private Application.ActivityLifecycleCallbacks activityLifecycleCallbacks;


    void init(Application application, String key, int videoQuality, boolean instantUpload) {
//        apiKey = key;
//        quality = videoQuality;
//        this.instantUpload = instantUpload;
//        this.application = application;




    }

    private void checkForVideos() {
        File recordingDir = new File(application.getFilesDir() +  "/recordings/");

        if(recordingDir.exists()) {
            if (recordingDir.listFiles().length > 0) {
                scheduleUploadJob();
            }
        }
    }

     void attachActivity(Activity activity) {

         if(application == null) {
             return;
         }

        if(touchView != null) {
            ((ViewGroup)touchView.getParent()).removeView(touchView);
        }


        createTouchView(activity);
    }


    private void setupAppState() {

        if(application == null) {
            return;
        }

        AppStateMonitor appStateMonitor = AppStateMonitor.create(application);
        appStateMonitor.addListener(new AppStateListener() {
            @Override
            public void onAppDidEnterForeground() {

            }

            @Override
            public void onAppDidEnterBackground() {
                stop();
//                application.unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks);
            }
        });


        activityLifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

            }

            @Override
            public void onActivityStarted(Activity activity) {

            }

            @Override
            public void onActivityResumed(Activity activity) {
                attachActivity(activity);
            }

            @Override
            public void onActivityPaused(Activity activity) {

            }

            @Override
            public void onActivityStopped(Activity activity) {

            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

            }

            @Override
            public void onActivityDestroyed(Activity activity) {

            }
        };

        application.registerActivityLifecycleCallbacks(activityLifecycleCallbacks);

        appStateMonitor.start();

    }

    private void calculateSizes() {

        if(application == null) {
            return;
        }

        DisplayMetrics metrics = application.getApplicationContext().getResources().getDisplayMetrics();

        int deviceWidth = metrics.widthPixels;
        int deviceHeight = metrics.heightPixels;

        double ratio;

        double targetSize;

        if(quality == QUALITY_LOW || quality == QUALITY_MEDIUM) {
            targetSize = 720;
        } else {
            targetSize = 1080;
        }

        if(deviceWidth < deviceHeight) {
            ratio = deviceWidth / Math.min(targetSize, deviceWidth);
        } else {
            ratio = deviceHeight / Math.min(targetSize, deviceHeight);
        }

        videoWidth = (int) (deviceWidth / ratio);
        videoHeight = (int) (deviceHeight / ratio);
    }


    private void createTouchView(Activity activity) {

        if(application == null) {
            return;
        }

        Resources resources = activity.getResources();
        touchSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, resources.getDisplayMetrics());

        ViewGroup viewGroup = (ViewGroup) activity.findViewById(android.R.id.content).getRootView();
        touchView = new ImageView(activity);
        touchView.setLayoutParams(new FrameLayout.LayoutParams(touchSize, touchSize));
        touchView.setImageResource(R.drawable.appcam_oval);
        touchView.setAlpha(0f);
        viewGroup.addView(touchView);

        interpolator = new AccelerateInterpolator();

    }

    private void prepareRecording() {

        if(application == null) {
            return;
        }


        final String directory = application.getFilesDir() + "/recordings/";


        final File folder = new File(directory);

        if (!folder.exists()) {
            folder.mkdir();
        }

        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setVideoEncodingBitRate(1024 * 1024 * quality);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoSize(videoWidth, videoHeight);
        mediaRecorder.setOutputFile(fileLocation);

        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }

        mediaRecorder.start();

    }

    private void setupMediaProjection() {

        DisplayMetrics metrics = application.getResources().getDisplayMetrics();

        prepareRecording();

        mediaProjection.createVirtualDisplay("ScreenCapture",
                videoWidth, videoHeight, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder.getSurface(), null, null);

    }

    void startRecording(String apiKey, File file) {

        this.apiKey = apiKey;
        this.quality = QUALITY_MEDIUM;

        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {

            Log.e(APP_CAM_LOG, "Requires at least Android Lollipop.");

            return;
        }

        if(application == null) {

            Log.e(APP_CAM_LOG, "Tried to start recording before AppCamProvider.init() is called.");

            return;
        }

        if(isRecording) {
            Log.e(APP_CAM_LOG, "AppCam is already recording.");
            return;
        }

        fileLocation = file.getAbsolutePath();

        mediaProjectionManager = (MediaProjectionManager) application.getApplicationContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        Intent i = new Intent(application.getApplicationContext(), InvisibleRequestPermissionActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        application.getApplicationContext().startActivity(i);

    }

     void startRecording(String apiKey) {
         startRecording(apiKey, new File(application.getFilesDir() +  "/recordings/" + System.currentTimeMillis() + ".mp4"));
    }

    void setApplication(Application application) {
        this.application = application;

        mediaRecorder = new MediaRecorder();

        calculateSizes();
        setupAppState();
        checkForVideos();


        hasInit = true;
    }


     boolean onActivityResult(int requestCode, int resultCode, Intent data) {

         if(application == null) {
             return false;
         }

        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);

        if(mediaProjection != null) {
            setupMediaProjection();
            isRecording = true;
            return true;
        } else {
            return false;
        }
    }

     void stop() {

         if(!isRecording) {
             return;
         }

        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
            }
        }catch (Exception e) {

        }

        try {
            if (mediaProjection != null) {
                mediaProjection.stop();
            }
        } catch (Exception e) {

        }

        scheduleUploadJob();

         touchView = null;
         isRecording = false;

     }

     private void scheduleUploadJob() {
         PersistableBundle bundle = new PersistableBundle();
         bundle.putString("file_location", fileLocation);

         JobInfo.Builder jobBuilder = new JobInfo.Builder(JOB_ID, new ComponentName(application, UploadIntentService.class));
         jobBuilder.setExtras(bundle);
         jobBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED);
         jobBuilder.setRequiresCharging(false);

         if(instantUpload) {
             jobBuilder.setOverrideDeadline(1000);
         }


         JobScheduler jobScheduler = (JobScheduler) application.getSystemService(Context.JOB_SCHEDULER_SERVICE);
         jobScheduler.schedule(jobBuilder.build());

         Log.i(APP_CAM_LOG, "File saved, upload scheduled");
     }

     void dispatchTouchEvent(MotionEvent ev) {

         if(application == null) {
             return;
         }

        if(touchView == null) {
            return;
        }

        if(!isRecording) {
            return;
        }



        touchView.setTranslationX(ev.getX() - touchSize/2);
        touchView.setTranslationY(ev.getY() - touchSize/2);


        touchView.clearAnimation();
        touchView.setAlpha(1f);
        touchView.animate().alpha(0f).setDuration(200).setStartDelay(0).setInterpolator(interpolator);

    }

}
