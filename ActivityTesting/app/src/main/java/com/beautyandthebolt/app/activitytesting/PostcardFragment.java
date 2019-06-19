package com.beautyandthebolt.app.activitytesting;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ExpandableListActivity;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.MotionEventCompat;
import android.text.Layout;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.JsonRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceLandmark;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.otaliastudios.cameraview.CameraListener;
import com.otaliastudios.cameraview.CameraUtils;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.Frame;
import com.otaliastudios.cameraview.FrameProcessor;
import com.otaliastudios.cameraview.Gesture;
import com.otaliastudios.cameraview.GestureAction;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.text.NumberFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.me.hardill.volley.multipart.MultipartRequest;

public class PostcardFragment extends Fragment  {
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        fragmentView = inflater.inflate(R.layout.fragment_postcard, null);
        checkAndRequestCameraPermission();
        displayMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        startCamera(fragmentView);
    }

    @Override
    public void onPause() {
        super.onPause();
        cameraView.stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cameraView.destroy();
    }

    private int PERMISSION_REQUEST_CODE = 1001;
    CameraView cameraView;
    public ImageView overlayView;
    public View fragmentView;
    private DisplayMetrics displayMetrics;

    FirebaseVisionFaceDetector detector;
    StorageReference storageRoot;
    StorageReference imagesThisDevice;
    String deviceName;
    RequestQueue twilioQueue;
    Map<String, String> twilioHeaders;

    public boolean setupFirebase(){
        try {
            FirebaseApp.initializeApp(fragmentView.getContext());
            storageRoot = FirebaseStorage.getInstance().getReference();
            deviceName = Settings.Secure.getString(getActivity().getContentResolver(), Settings.Secure.ANDROID_ID);
            Log.d("firebase-startup", deviceName);
            imagesThisDevice = storageRoot.child(deviceName+"-postcards");
            FirebaseVisionFaceDetectorOptions.Builder options = new FirebaseVisionFaceDetectorOptions.Builder();
            options.setLandmarkType(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS);
            detector = FirebaseVision.getInstance().getVisionFaceDetector(options.build());
            Log.d("firebase-startup", "yup");
            return true;
        }
       catch (Exception e){
            Log.d("firebase-startup", "nope: "+ e.toString());
            return false;
        }
    }

    String twilioBase = "https://api.twilio.com/2010-04-01/Accounts/";
    String accountSID = "AC90c91bd2afa942cc512ddc449a1a98b1";
    String accountAuthToken = "cc6fe6bf8219018bda15067c22e7b8e7";
    String twilioMode = "/Messages.json";
    String twilioSendNumber = "+12168684601";

    public boolean setupTwilio(){
        twilioQueue = Volley.newRequestQueue(getActivity());
        twilioHeaders = new HashMap<>();
        String credentials = accountSID + ":" + accountAuthToken;
        String auth = "Basic " + Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
        twilioHeaders.put("Authorization", auth);
        return true;
    }

    public boolean sendTwilioSMS(String phoneNumber, String message){
        String url = twilioBase+accountSID+twilioMode;
        MultipartRequest request = new MultipartRequest(url, twilioHeaders,getPostResponseListener(), getPostErrorListener());
        request.addPart(new MultipartRequest.FormPart("From", twilioSendNumber));
        request.addPart(new MultipartRequest.FormPart("To", phoneNumber));
        request.addPart(new MultipartRequest.FormPart("Body", message));
        twilioQueue.add(request);
        return true;
    }

    public boolean sendTwilioMMS(String phoneNumber, String message, String photoUrl){
        String url = twilioBase+accountSID+twilioMode;
        MultipartRequest request = new MultipartRequest(url, twilioHeaders,getPostResponseListener(), getPostErrorListener());
        request.addPart(new MultipartRequest.FormPart("From", twilioSendNumber));
        request.addPart(new MultipartRequest.FormPart("To", phoneNumber));
        request.addPart(new MultipartRequest.FormPart("Body", message));
        request.addPart(new MultipartRequest.FormPart("MediaUrl", photoUrl));
        twilioQueue.add(request);
        return true;
    }

    private Response.Listener<NetworkResponse> getPostResponseListener(){
        return new Response.Listener<NetworkResponse>() {
            @Override
            public void onResponse(NetworkResponse networkResponse) {
                String json;
                if (networkResponse != null && networkResponse.data != null) {
                    try {
                        json = new String(networkResponse.data,
                                HttpHeaderParser.parseCharset(networkResponse.headers));
                        Log.d("twilio-startup", json);
                    } catch (UnsupportedEncodingException e) {
                        Log.d("twilio-startup", e.getMessage());
                    }
                }
            }
        };
    }

    private Response.ErrorListener getPostErrorListener(){
        return new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                String json;
                if (error.networkResponse != null && error.networkResponse.data != null) {
                    try {
                        json = new String(error.networkResponse.data,
                                HttpHeaderParser.parseCharset(error.networkResponse.headers));
                        Log.d("twilio-startup", json);
                    } catch (UnsupportedEncodingException e) {
                        Log.d("twilio-startup", e.getMessage());
                    }
                }
            }
        };
    }

    public boolean firebaseLock = false;

    public boolean saveNextFrame = false;

    AlertDialog pictureDialog;

    Bitmap currentUpload;
    public String currentPhoneNumber;
    public String currentEmailAddress;
    public View photoDialogView;
    public ProgressDialog progress;
    public int love_letter_emoji = 0x1F48C;
    public List<FirebaseVisionFace> currentFaces;

    public boolean MakePictureDialog(Context c, Bitmap bmp){
        saveNextFrame = false;

        Log.d("picture", "Bitmap is null?: "+ (bmp == null));
        AlertDialog.Builder pictureDialogBuilder = new AlertDialog.Builder(c);
        LayoutInflater detailFactory = LayoutInflater.from(c);
        photoDialogView = detailFactory.inflate(R.layout.picture_check_layout, null);

        ((Button) photoDialogView.findViewById(R.id.retakePictureButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("picture", "retake picture");
                pictureDialog.dismiss();
            }
        });

        Log.d("picture", "Photo Resolution: W:"+bmp.getWidth()+" H:"+bmp.getHeight());

        Bitmap overlay = BitmapFactory.decodeResource(getResources(), overlayReferences[currentOverlay]);
        Log.d("picture", "Overlay Resolution: W:"+overlay.getWidth()+" H:"+overlay.getHeight());

        Bitmap scaled_photo = Bitmap.createScaledBitmap(bmp, (int)(bmp.getWidth()*1.2), (int)(bmp.getHeight()*1.2), false);
        Log.d("picture", "Scaled Overlay Resolution: W:"+scaled_photo.getWidth()+" H:"+scaled_photo.getHeight());

        currentUpload  = makeOverlay(scaled_photo, overlay);
        Log.d("picture", "Output Resolution: W:"+currentUpload.getWidth()+" H:"+currentUpload.getHeight());

        ((ImageView) photoDialogView.findViewById(R.id.dialog_confirmPictureImage)).setImageBitmap(currentUpload);


        ((Button) photoDialogView.findViewById(R.id.confirmPictureButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentPhoneNumber = "+" + (((EditText) photoDialogView.findViewById(R.id.photoPhoneNumber)).getText().toString()).replaceAll("[\\s\\-()]", "");
                if (currentPhoneNumber.length() == 12) {
                    progress = new ProgressDialog(getActivity());

                    progress.setMessage("Sending your postcard "+ getEmojiByUnicode(love_letter_emoji));
                    progress.show();

                    Log.d("picture", "Target Phone Number: " + currentPhoneNumber);
                    sendTwilioSMS(currentPhoneNumber, "Thanks for stopping by the Beauty and the Bolt booth at CES 2019. Your digital postcard is processing and you should get it in a few minutes!");
                    Log.d("picture", "trying firebase upload");
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    currentUpload.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                    byte[] data = baos.toByteArray();
                    String filename = Calendar.getInstance().getTime().toString() + ".jpg";

                    StorageReference thisFile = imagesThisDevice.child(filename);
                    UploadTask uploadTask = thisFile.putBytes(data);
                    uploadTask.addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception exception) {
                            Log.d("picture", exception.toString());
                            Log.d("picture", "upload failed");
                        }
                    }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                            Log.d("picture", "upload succeeded getting URL");
                            taskSnapshot.getMetadata().getReference().getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                                @Override
                                public void onSuccess(Uri publicURL) {
                                    progress.dismiss();
                                    Log.d("picture", "URL Succeeded: " + publicURL.toString());
                                    sendTwilioMMS(currentPhoneNumber, "Here's your postcard! Share yours on Instagram with #BrilliantIsBeautiful", publicURL.toString());
                                }
                            });
                        }
                    });
                    pictureDialog.dismiss();
                }
                else{
                    Toast toast = Toast.makeText(getActivity(), "Please enter a valid phone number with country and area codes", Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                }
            }
        });
        pictureDialogBuilder.setView(photoDialogView);
        pictureDialog = pictureDialogBuilder.create();
        pictureDialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE );
        pictureDialog.show();
        pictureDialog.getWindow().getDecorView().setSystemUiVisibility(getActivity().getWindow().getDecorView().getSystemUiVisibility());
        pictureDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        pictureDialog.getWindow().setLayout((int) (displayMetrics.widthPixels * 0.85f), (int) (displayMetrics.heightPixels * 0.85f)); //Controlling width and height

        return true;
    }

    public String getEmojiByUnicode(int unicode){
        return new String(Character.toChars(unicode));
    }

    public static Bitmap makeOverlay(Bitmap bottom, Bitmap top) {
        int bitmap1Width = bottom.getWidth();
        int bitmap1Height = bottom.getHeight();
        int bitmap2Width = top.getWidth();
        int bitmap2Height = top.getHeight();

        float marginLeft = (float) (bitmap2Width * 0.5 - bitmap1Width * 0.5);
        float marginTop = (float) (bitmap2Height * 0.5 - bitmap1Height * 0.5);

        Bitmap finalBitmap = Bitmap.createBitmap(bitmap2Width, bitmap2Height, bottom.getConfig());
        Canvas canvas = new Canvas(finalBitmap);
        canvas.drawBitmap(FlipBitmapHorizontal(bottom), marginLeft, marginTop, null);
        canvas.drawBitmap(top, 0,0, null);
        return finalBitmap;
    }

    public static Bitmap FlipBitmapHorizontal(Bitmap source) {
        Matrix matrix = new Matrix();
        matrix.postScale(-1, 1, source.getWidth()/2f, source.getHeight()/2f);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    public void startCamera(View v){

        Button takePictureButton = (Button) v.findViewById(R.id.takePictureButton);
        takePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("picture", "shutter pressed");
                saveNextFrame = true;
            }
        });

        cameraView = (CameraView) v.findViewById(R.id.camera_view);

        cameraView.start();

        overlayView = (ImageView) v.findViewById(R.id.overlay_view);
        mDetector = new GestureDetectorCompat(getActivity(), new MyGestureListener());
        overlayView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mDetector.onTouchEvent(event);
                return true;
            }
        });

        switchToOverlay(currentOverlay);

        setupFirebase();
        setupTwilio();

        cameraView.setCropOutput(true);
        cameraView.mapGesture(Gesture.TAP, GestureAction.FOCUS_WITH_MARKER); // Tap to focus!
        cameraView.mapGesture(Gesture.TAP, GestureAction.FOCUS_WITH_MARKER); // Tap to focus!

        cameraView.addFrameProcessor(new FrameProcessor() {
            @Override
            public void process(@NonNull final Frame frame) {
                if(firebaseLock != true) {
                    firebaseLock = true;

                    int rotation = frame.getRotation() / 90;
                    FirebaseVisionImageMetadata.Builder imageProcessingBuilder = new FirebaseVisionImageMetadata.Builder();
                    imageProcessingBuilder.setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21);
                    imageProcessingBuilder.setWidth(frame.getSize().getWidth());
                    imageProcessingBuilder.setHeight(frame.getSize().getHeight());
                    //Log.d("cameraSize", frame.getSize().toString());
                    imageProcessingBuilder.setRotation(rotation);
                    FirebaseVisionImage firebaseVisionImage = FirebaseVisionImage.fromByteArray(frame.getData(), imageProcessingBuilder.build());
                    firebaseVisionImage.getBitmapForDebugging();
                    if(saveNextFrame) {
                        MakePictureDialog(getActivity(), firebaseVisionImage.getBitmapForDebugging());
                    }
                    if(detector != null) {
                        detector.detectInImage(firebaseVisionImage).addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionFace>>() {
                            @Override
                            public void onSuccess(List<FirebaseVisionFace> firebaseVisionFaces) {
                                currentFaces = firebaseVisionFaces;
                                firebaseLock = false;
                            }
                        });
                    }
                }
            }
        });
    }

    private GestureDetectorCompat mDetector;

    class MyGestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final String DEBUG_TAG = "Gestures";

        @Override
        public boolean onFling(MotionEvent event1, MotionEvent event2, float velocityX, float velocityY) {

            if (velocityX >0){
                Log.d("touch", "Left");
                switchToOverlay(currentOverlay+1);
            }
            if(velocityX<0){
                Log.d("touch", "Right");
                switchToOverlay(currentOverlay-1);
            }
            return true;
        }
    }

    public int currentOverlay = 0;
    public int[] overlayReferences = new int[]{R.drawable.stem_builds_future, R.drawable.support_stem};

    public void switchToOverlay(int index){
        if(index<0){
            currentOverlay = overlayReferences.length-1;
        }
        else {
            if (index < overlayReferences.length) {
                currentOverlay = index;
            } else {
                currentOverlay = 0;
            }
        }
        overlayView.setImageResource(overlayReferences[currentOverlay]);

    }

    private void checkAndRequestCameraPermission(){
        if(ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
        }
        else{
            startCamera(fragmentView) ;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults){
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if(Manifest.permission.CAMERA == permissions[0] && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                startCamera(fragmentView);
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


    // The following is facial recognition overlay code that isn't done yet

//    public ImageOverlay[] overlays = new ImageOverlay[]{
//            new ImageOverlay(R.drawable.support_stem, false),
//            new ImageOverlay(R.drawable.STEM_builds_the_FUTURE, false),
//            new ImageOverlay(R.drawable.glasses, true)
//    };

//    public class ImageOverlay{
//        int resourceReference;
//        boolean reactive;
//
//        public ImageOverlay(int resourceReference, boolean reactive){
//            this.resourceReference = resourceReference;
//            this.reactive = reactive;
//        }
//
//        public Bitmap getOverlayImage(List<FirebaseVisionFace> faces){
//            if(!reactive) {
//                Bitmap overlay = BitmapFactory.decodeResource(getResources(), resourceReference);
//                return overlay;
//            }
//            else{
//                Bitmap resource = BitmapFactory.decodeResource(getResources(), resourceReference);
//                Bitmap overlay = Bitmap.createBitmap(1120, 840, resource.getConfig());
//
//                Canvas canvas = new Canvas(overlay);
//
//                for(FirebaseVisionFace face : faces){
//                    FirebaseVisionFaceLandmark leftEye =  face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EYE);
//                    FirebaseVisionFaceLandmark rightEye = face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EYE);
//                    if (leftEye != null && rightEye != null) {
//                        float eyeDistance = leftEye.getPosition().getX() - rightEye.getPosition().getX();
//                        float delta = (int)(eyeDistance / 2);
//                        Rect glassesRect = new Rect(
//                                (int)(leftEye.getPosition().getX() - delta),
//                                (int)(leftEye.getPosition().getY() - delta),
//                                (int)(rightEye.getPosition().getX() + delta),
//                                (int)(rightEye.getPosition().getY() + delta));
//                        canvas.drawBitmap(resource, null, glassesRect, null);
//                    }
//                }
//                return overlay;
//            }
//
//        }
//    }



}
