package bashbug.cloud2mesh;

/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.atap.tango.ux.TangoUx;
import com.google.atap.tango.ux.TangoUxLayout;
import com.google.atap.tango.ux.UxExceptionEvent;
import com.google.atap.tango.ux.UxExceptionEventListener;
import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoAreaDescriptionMetaData;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoCameraPreview;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

import com.projecttango.tangoutils.ModelMatCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.FloatBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;


/**
 * Main Activity class for the Point Cloud Sample. Handles the connection to the {@link Tango}
 * service and propagation of Tango XyzIj data to OpenGL and Layout views. OpenGL rendering logic is
 * delegated to the {@link PCRenderer} class.
 */
public class PointCloudActivity extends BaseActivity implements View.OnClickListener {

    private static final String TAG = PointCloudActivity.class.getSimpleName();
    private Tango mTango;
    private TangoConfig mConfig;

    private PCRenderer mRenderer;
    private GLSurfaceView mGLView;

    private TextView mSavedPCLFilesTextView;

    private Button mFirstPersonButton;
    private Button mThirdPersonButton;
    private Button mTopDownButton;

    private Switch mRecordPCLSwitch;
    private boolean mRecordPCL = false;
    private int mPclFileCounter;
    private int mStoreEachThirdPCL;

    private int mD2ADF;
    private int mD2START;
    private int mADF2START;
    // Camera
    private TangoCameraPreview mCameraView;

    // depth image
    TangoCameraIntrinsics mDepthCameraIntrinsics;

    // PCL
    private int count;
    private int mPreviousPoseStatus;
    private int mPointCount;
    private double mCurrentTimeStamp;
    private TangoPoseData[] mPoses;

    private TextView mStart2DeviceTranslationTextView;
    private TextView mAdf2DeviceTranslationTextView;
    private TextView mAdf2StartTranslationTextView;

    private TextView mStart2DeviceQuatTextView;
    private TextView mAdf2DeviceQuatTextView;
    private TextView mAdf2StartQuatTextView;
    private TextView mUUIDTextView;

    private TextView mStart2DevicePoseStatusTextView;
    private TextView mAdf2DevicePoseStatusTextView;
    private TextView mAdf2StartPoseStatusTextView;
    private TextView mStart2DevicePoseCountTextView;
    private TextView mAdf2DevicePoseCountTextView;
    private TextView mAdf2StartPoseCountTextView;

    private int mStart2DevicePoseCount;
    private int mAdf2DevicePoseCount;
    private int mAdf2StartPoseCount;
    private int mStart2DevicePreviousPoseStatus;
    private int mAdf2DevicePreviousPoseStatus;
    private int mAdf2StartPreviousPoseStatus;

    private boolean mIsRelocalized = false;
    private String mCurrentUUID;

    private Toast mRelocalizedToastView;
    private TangoUx mTangoUx;
    private TangoUxLayout mTangoUxLayout;

    private boolean mRecordWithADF;

    private float[] device_T_depth;
    private float[] imu_T_depth;
    private float[] imu_T_device;

    private static final int UPDATE_INTERVAL_MS = 100;
    private static final DecimalFormat threeDec = new DecimalFormat("00.000");
    public static Object poseLock = new Object();
    public static Object depthLock = new Object();
    public static Object imageLock = new Object();

    private static int CAPTURE_EVERY_N = 3;

    /*
     * This is an advanced way of using UX exceptions. In most cases developers can just use the in
     * built exception notifications using the Ux Exception layout. In case a developer doesn't want
     * to use the default Ux Exception notifications, he can set the UxException listener as shown
     * below.
     * In this example we are just logging all the ux exceptions to logcat, but in a real app,
     * developers should use these exceptions to contextually notify the user and help direct the
     * user in using the device in a way Tango service expects it.
     */
    private UxExceptionEventListener mUxExceptionListener = new UxExceptionEventListener() {

        @Override
        public void onUxExceptionEvent(UxExceptionEvent uxExceptionEvent) {
            if(uxExceptionEvent.getType() == UxExceptionEvent.TYPE_LYING_ON_SURFACE){
                Log.i(TAG, "Device lying on surface ");
            }
            if(uxExceptionEvent.getType() == UxExceptionEvent.TYPE_FEW_DEPTH_POINTS){
                Log.i(TAG, "Very few depth points in point cloud " );
            }
            if(uxExceptionEvent.getType() == UxExceptionEvent.TYPE_FEW_FEATURES){
                Log.i(TAG, "Invalid poses in MotionTracking ");
            }
            if(uxExceptionEvent.getType() == UxExceptionEvent.TYPE_INCOMPATIBLE_VM){
                Log.i(TAG, "Device not running on ART");
            }
            if(uxExceptionEvent.getType() == UxExceptionEvent.TYPE_MOTION_TRACK_INVALID){
                Log.i(TAG, "Invalid poses in MotionTracking ");
            }
            if(uxExceptionEvent.getType() == UxExceptionEvent.TYPE_MOVING_TOO_FAST){
                Log.i(TAG, "Invalid poses in MotionTracking ");
            }
            if(uxExceptionEvent.getType() == UxExceptionEvent.TYPE_OVER_EXPOSED){
                Log.i(TAG, "Camera Over Exposed");
            }
            if(uxExceptionEvent.getType() == UxExceptionEvent.TYPE_TANGO_SERVICE_NOT_RESPONDING){
                Log.i(TAG, "TangoService is not responding ");
            }
            if(uxExceptionEvent.getType() == UxExceptionEvent.TYPE_TANGO_UPDATE_NEEDED){
                Log.i(TAG, "Device not running on ART");
            }
            if(uxExceptionEvent.getType() == UxExceptionEvent.TYPE_UNDER_EXPOSED){
                Log.i(TAG, "Camera Under Exposed " );
            }

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_jpoint_cloud);

        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();

        if(bundle != null)
        {
            if((boolean)bundle.get("withADF") == true ){
                mRecordWithADF = true;
                setTitle("Record Point Cloud with ADF");
            } else {
                mRecordWithADF = false;
                setTitle("Record Point Cloud without ADF");
            }
        }

        mSavedPCLFilesTextView = (TextView) findViewById(R.id.saved_pcl_files_textview);

        mAdf2DeviceTranslationTextView = (TextView) findViewById(R.id.adf2devicePose);
        mStart2DeviceTranslationTextView = (TextView) findViewById(R.id.start2devicePose);
        mAdf2StartTranslationTextView = (TextView) findViewById(R.id.adf2startPose);

        mAdf2DeviceQuatTextView = (TextView) findViewById(R.id.adf2deviceQuat);
        mStart2DeviceQuatTextView = (TextView) findViewById(R.id.start2deviceQuat);
        mAdf2StartQuatTextView = (TextView) findViewById(R.id.adf2startQuat);

        mAdf2DevicePoseStatusTextView = (TextView) findViewById(R.id.adf2deviceStatus);
        mStart2DevicePoseStatusTextView = (TextView) findViewById(R.id.start2deviceStatus);
        mAdf2StartPoseStatusTextView = (TextView) findViewById(R.id.adf2startStatus);

        mAdf2DevicePoseCountTextView = (TextView) findViewById(R.id.adf2devicePosecount);
        mStart2DevicePoseCountTextView = (TextView) findViewById(R.id.start2devicePosecount);
        mAdf2StartPoseCountTextView = (TextView) findViewById(R.id.adf2startPosecount);

        mFirstPersonButton = (Button) findViewById(R.id.first_person_button);
        mFirstPersonButton.setOnClickListener(this);
        mThirdPersonButton = (Button) findViewById(R.id.third_person_button);
        mThirdPersonButton.setOnClickListener(this);
        mTopDownButton = (Button) findViewById(R.id.top_down_button);
        mTopDownButton.setOnClickListener(this);

        // Toast for relocalized area. Shows up for 2sek
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.toast,
                (ViewGroup) findViewById(R.id.toast_layout_root));
        TextView text = (TextView) layout.findViewById(R.id.text);
        text.setText("Area localized");
        mRelocalizedToastView = new Toast(getApplicationContext());

        int offset = Math.round(30 * getApplicationContext().getResources().getDisplayMetrics().density);

        mRelocalizedToastView.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, offset);
        mRelocalizedToastView.setDuration(Toast.LENGTH_SHORT);
        mRelocalizedToastView.setView(layout);

        // Camera view
        mCameraView = (TangoCameraPreview) findViewById(R.id.gl_camera_view);
        mCameraView.setZOrderOnTop(true);

        // Store pcl to file buttons
        mRecordPCLSwitch = (Switch) findViewById(R.id.record_pcl_switch);
        mRecordPCLSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mRecordPCL = isChecked;
            }
        });

        startActivityForResult(
                Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_MOTION_TRACKING),
                0);
        startActivityForResult(
                Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_ADF_LOAD_SAVE),
                1);

        mTango = new Tango(this);
        mConfig = new TangoConfig();
        mConfig = mTango.getConfig(TangoConfig.CONFIG_TYPE_CURRENT);

        if (mRecordWithADF) {
            // Returns a list of ADFs with their UUIDs
            ArrayList<String> fullUUIDList = mTango.listAreaDescriptions();

            // Load the latest ADF if ADFs are found.
            if (fullUUIDList.size() > 0) {
                mConfig.putString(TangoConfig.KEY_STRING_AREADESCRIPTION,
                        fullUUIDList.get(fullUUIDList.size() - 1));
                TangoAreaDescriptionMetaData metaData = mTango.loadAreaDescriptionMetaData(fullUUIDList.get(fullUUIDList.size() - 1));
                //mLatestADFFileView.setText(String.valueOf(metaData.get("name"));
                Log.e("ADF File", fullUUIDList.get(fullUUIDList.size() - 1) + " .... loaded");
                mCurrentUUID = fullUUIDList.get(fullUUIDList.size() - 1);
            }
        }

        mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);

        mTangoUx = new TangoUx.Builder(this).build();
        mTangoUxLayout = (TangoUxLayout) findViewById(R.id.layout_tango);
        mTangoUx = new TangoUx.Builder(this).setTangoUxLayout(mTangoUxLayout).build();
        mTangoUx.setUxExceptionEventListener(mUxExceptionListener);

        // PCL view
        int maxDepthPoints = mConfig.getInt("max_point_cloud_elements");
        mRenderer = new PCRenderer(maxDepthPoints);
        mGLView = (GLSurfaceView) findViewById(R.id.gl_surface_view);
        mGLView.setEGLContextClientVersion(2);
        mGLView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        mGLView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        mGLView.setRenderer(mRenderer);

        // Set the number of loop closures to zero at start.
        mStart2DevicePoseCount = 0;
        mAdf2DevicePoseCount = 0;
        mAdf2StartPoseCount = 0;
        mPclFileCounter = 0;
        mPoses = new TangoPoseData[3];

        startUIThread();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mTangoUx.stop();
        try {
            mTango.disconnect();
        } catch (TangoErrorException e) {
            Toast.makeText(getApplicationContext(), R.string.TangoError, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mTangoUx.start();

        Log.i(TAG, "onResumed");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == 0) {
            Log.i(TAG, "Triggered");
            // Make sure the request was successful
            if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, R.string.motiontrackingpermission, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        } else if (requestCode == 1) {
            Log.i(TAG, "Triggered");
            // Make sure the request was successful
            if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, R.string.motiontrackingpermission, Toast.LENGTH_LONG).show();
                finish();
            }
            connectTango();
        }
    }

    private void connectTango () {
        try {
            setTangoListeners();
        } catch (TangoErrorException e) {
            Toast.makeText(this, R.string.TangoError, Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(getApplicationContext(), R.string.motiontrackingpermission,
                    Toast.LENGTH_SHORT).show();
        }

        try {
            mTango.connect(mConfig);
        } catch (TangoOutOfDateException outDateEx) {
            if (mTangoUx != null) {
                mTangoUx.onTangoOutOfDate();
            }
        } catch (TangoErrorException e) {
            Toast.makeText(getApplicationContext(), R.string.TangoError, Toast.LENGTH_SHORT)
                    .show();
        }
        setUpExtrinsics();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.first_person_button:
                mRenderer.setFirstPersonView();
                break;
            case R.id.third_person_button:
                mRenderer.setThirdPersonView();
                break;
            case R.id.top_down_button:
                mRenderer.setTopDownView();
                break;
            default:
                Log.w(TAG, "Unrecognized button click.");
                return;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mRenderer.onTouchEvent(event);
    }

    private void setUpExtrinsics() {
        TangoPoseData pose_imu_T_device = new TangoPoseData();
        TangoCoordinateFramePair framePair = new TangoCoordinateFramePair();

        // Set device to imu matrix in Model Matrix Calculator.
        framePair.baseFrame = TangoPoseData.COORDINATE_FRAME_IMU;
        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_DEVICE;
        try {
            pose_imu_T_device = mTango.getPoseAtTime(0.0, framePair);
        } catch (TangoErrorException e) {
            Toast.makeText(getApplicationContext(), R.string.TangoError, Toast.LENGTH_SHORT).show();
        }

        mRenderer.getModelMatCalculator().SetDevice2IMUMatrix(
                pose_imu_T_device.getTranslationAsFloats(), pose_imu_T_device.getRotationAsFloats());

        // Set color camera to imu matrix in Model Matrix Calculator.
        TangoPoseData pose_imu_T_cam = new TangoPoseData();

        framePair.baseFrame = TangoPoseData.COORDINATE_FRAME_IMU;
        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR;
        try {
           pose_imu_T_cam = mTango.getPoseAtTime(0.0, framePair);
        } catch (TangoErrorException e) {
            Toast.makeText(getApplicationContext(), R.string.TangoError, Toast.LENGTH_SHORT).show();
        }
        mRenderer.getModelMatCalculator().SetColorCamera2IMUMatrix(
                pose_imu_T_cam.getTranslationAsFloats(), pose_imu_T_cam.getRotationAsFloats());

        // depth frame wrt to imu frame
        TangoPoseData pose_imu_T_depth = new TangoPoseData();
        framePair.baseFrame = TangoPoseData.COORDINATE_FRAME_IMU;
        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH;
        try {
            pose_imu_T_depth = mTango.getPoseAtTime(0.0, framePair);
        } catch (TangoErrorException e) {
            Toast.makeText(getApplicationContext(), R.string.TangoError, Toast.LENGTH_SHORT).show();
        }

        // transform between device and depth camera
        float[] rotation_imu_T_device = pose_imu_T_device.getRotationAsFloats();
        float[] translation_imu_T_device = pose_imu_T_device.getTranslationAsFloats();
        float[] rotation_imu_T_depth = pose_imu_T_depth.getRotationAsFloats();
        float[] translation_imu_T_depth = pose_imu_T_depth.getTranslationAsFloats();

        imu_T_device = new float[16];
        Matrix.setIdentityM(imu_T_device, 0);
        imu_T_device = ModelMatCalculator.quaternionMatrixOpenGL(rotation_imu_T_device);
        imu_T_device[12] += translation_imu_T_device[0];
        imu_T_device[13] += translation_imu_T_device[1];
        imu_T_device[14] += translation_imu_T_device[2];

        imu_T_depth = new float[16];
        Matrix.setIdentityM(imu_T_depth, 0);
        imu_T_depth = ModelMatCalculator.quaternionMatrixOpenGL(rotation_imu_T_depth);
        imu_T_depth[12] += translation_imu_T_depth[0];
        imu_T_depth[13] += translation_imu_T_depth[1];
        imu_T_depth[14] += translation_imu_T_depth[2];

        float[] device_T_imu = new float[16];
        Matrix.setIdentityM(device_T_imu, 0);
        Matrix.invertM(device_T_imu, 0, imu_T_device, 0);

        device_T_depth = new float[16];
        Matrix.setIdentityM(device_T_depth, 0);
        Matrix.multiplyMM(device_T_depth, 0, device_T_imu, 0, imu_T_depth, 0);

        mDepthCameraIntrinsics = mTango.getCameraIntrinsics(TangoCameraIntrinsics.TANGO_CAMERA_DEPTH);

        /*TangoCameraIntrinsics depth = mTango.getCameraIntrinsics(TangoCameraIntrinsics.TANGO_CAMERA_DEPTH);

        Log.e("depth", "");
        Log.e("", String.valueOf(depth.width));
        Log.e("",String.valueOf(depth.height));

        TangoCameraIntrinsics color = mTango.getCameraIntrinsics(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
        Log.e("color", "");
        Log.e("",String.valueOf(color.width));
        Log.e("",String.valueOf(color.height));

        TangoCameraIntrinsics rgbir = mTango.getCameraIntrinsics(TangoCameraIntrinsics.TANGO_CAMERA_RGBIR);
        Log.e("rgbir", "");
        Log.e("",String.valueOf(rgbir.width));
        Log.e("",String.valueOf(rgbir.height));*/


    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    private void setTangoListeners() {

        // Set Tango Listeners for Poses Device wrt Start of Service, Device wrt
        // ADF and Start of Service wrt ADF
        final ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();

        // if no adf available
        mD2START = 0;
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
        // synchronize pcl with adf data
        mD2ADF = 1;
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
        // test if loop closed
        mADF2START = 2;
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE));

        // Listen for new RGB camera data
        if(mTango != null) {
            mCameraView.connectToTangoCamera(mTango, TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
        } else {
            Log.e("PointCloudFragment", "mTango == null");
        }

        // Listen for new Tango data
        mTango.connectListener(framePairs, new Tango.OnTangoUpdateListener() {

            @Override
            public void onPoseAvailable(final TangoPoseData pose) {
                // Passing in the pose data to UX library produce exceptions.
                if (mTangoUx != null) {
                    mTangoUx.updatePoseStatus(pose.statusCode);
                }
                // Make sure to have atomic access to Tango Pose Data so that
                // render loop doesn't interfere while Pose call back is updating
                // the data.
                synchronized (poseLock) {

                    // Check for Device wrt ADF pose. Only if ADF is localized, after one loop closure
                    if (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
                            && pose.targetFrame == TangoPoseData.COORDINATE_FRAME_DEVICE) {
                        mPoses[0] = pose;
                        if (mAdf2DevicePreviousPoseStatus != pose.statusCode) {
                            // Set the count to zero when status code changes.
                            mAdf2DevicePoseCount = 0;
                        }

                        mAdf2DevicePreviousPoseStatus = pose.statusCode;
                        mAdf2DevicePoseCount++;

                        // Device wrt Start of Service pose, no localization yet
                    } else if (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE
                            && pose.targetFrame == TangoPoseData.COORDINATE_FRAME_DEVICE) {
                        mPoses[1] = pose;
                        if (mStart2DevicePreviousPoseStatus != pose.statusCode) {
                            // Set the count to zero when status code changes.
                            mStart2DevicePoseCount = 0;
                        }

                        mStart2DevicePreviousPoseStatus = pose.statusCode;
                        mStart2DevicePoseCount++;

                        // Start of Service wrt ADF pose(This pose determines if the device is relocalized or not).
                    } else if (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
                            && pose.targetFrame == TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE) {
                        mPoses[2] = pose;

                        if (mAdf2StartPreviousPoseStatus != pose.statusCode) {
                            // Set the count to zero when status code changes.
                            mAdf2StartPoseCount = 0;
                        }
                        mAdf2StartPreviousPoseStatus = pose.statusCode;
                        mAdf2StartPoseCount++;

                        if (pose.statusCode == TangoPoseData.POSE_VALID && mAdf2StartPoseCount > 1) {
                            mIsRelocalized = true;
                            mRelocalizedToastView.show();
                        } else {
                            mIsRelocalized = false;
                        }
                    }

                    if (mPreviousPoseStatus != pose.statusCode) {
                        count = 0;
                    }
                    count++;
                    mPreviousPoseStatus = pose.statusCode;
                    if(!mRenderer.isValid()){
                        return;
                    }
                }
            }

            @Override
            public void onXyzIjAvailable(final TangoXyzIjData xyzIj) {
                if(mTangoUx!=null){
                    mTangoUx.updateXyzCount(xyzIj.xyzCount);
                }

                mStoreEachThirdPCL++;

                // Make sure to have atomic access to TangoXyzIjData so that
                // render loop doesn't interfere while onXYZijAvailable callback is updating
                // the point cloud data.
                synchronized (depthLock) {
                    mCurrentTimeStamp = xyzIj.timestamp;

                    try {

                        TangoPoseData pointCloudPose;
                        if (mRecordWithADF) {
                            if (mIsRelocalized) {
                                // get pose of ADF
                                pointCloudPose = mTango.getPoseAtTime(mCurrentTimeStamp,
                                        framePairs.get(mD2ADF));

                                if (pointCloudPose.statusCode == TangoPoseData.POSE_VALID) {
                                    if (mRecordPCL && mStoreEachThirdPCL % CAPTURE_EVERY_N == 0) {

                                        /*SavePointCloudTask sPCLt = new SavePointCloudTask(pointCloudPose.getTranslationAsFloats(), pointCloudPose.getRotationAsFloats(), copyXyz(xyzIj.xyz), mPclFileCounter, true);

                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                                            sPCLt.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                        } else {
                                            sPCLt.execute();
                                        }
                                        mPclFileCounter++;*/

                                    }
                                }
                            }else {
                                pointCloudPose = mTango.getPoseAtTime(mCurrentTimeStamp,
                                        framePairs.get(mD2START));
                            }
                        } else {
                            // get pose of Device
                            pointCloudPose = mTango.getPoseAtTime(mCurrentTimeStamp,
                                    framePairs.get(mD2START));

                            if (pointCloudPose.statusCode == TangoPoseData.POSE_VALID) {
                                if (mRecordPCL && mStoreEachThirdPCL % CAPTURE_EVERY_N == 0) {

                                    //SavePointCloudTask sPCLt = new SavePointCloudTask(transformPose(pointCloudPose), copyXyz2(xyzIj.xyz), (int) (mCurrentTimeStamp*1000));
                                    //SavePointCloudTask sPCLt = new SavePointCloudTask(pointCloudPose.getTranslationAsFloats(), pointCloudPose.getRotationAsFloats(), copyXyz(xyzIj.xyz), (int) (mCurrentTimeStamp*1000), true);
                                    //SavePointCloudTask sPCLt2 = new SavePointCloudTask(pointCloudPose.getTranslationAsFloats(), pointCloudPose.getRotationAsFloats(), copyXyz2(xyzIj.xyz), (int) (mCurrentTimeStamp*1000), false);

                                    SaveDepthImageTask saveDepthImageTask = new SaveDepthImageTask(transformPose(pointCloudPose), copyXyz2(xyzIj.xyz), mDepthCameraIntrinsics, (int) (mCurrentTimeStamp*1000));

                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                                        //sPCLt.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                        saveDepthImageTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                    } else {
                                        //sPCLt.execute();
                                        saveDepthImageTask.execute();
                                    }

                                    mPclFileCounter++;

                                }
                            }
                        }

                        if (pointCloudPose.statusCode == TangoPoseData.POSE_VALID) {
                            if(!mRenderer.isValid()){
                                return;
                            }

                            mRenderer.getModelMatCalculator().updateModelMatrix(
                                    pointCloudPose.getTranslationAsFloats(), pointCloudPose.getRotationAsFloats());
                            mRenderer.updateViewMatrix();

                            mPointCount = xyzIj.xyzCount;

                            mRenderer.getPointCloud().UpdatePoints(xyzIj.xyz);
                            mRenderer.getModelMatCalculator().updatePointCloudModelMatrix(
                                    pointCloudPose.getTranslationAsFloats(),
                                    pointCloudPose.getRotationAsFloats());
                            mRenderer.getPointCloud().setModelMatrix(
                                    mRenderer.getModelMatCalculator().getPointCloudModelMatrixCopy());
                        }
                    } catch (TangoErrorException e) {
                        Toast.makeText(getApplicationContext(), R.string.TangoError,
                                Toast.LENGTH_SHORT).show();
                    } catch (TangoInvalidException e) {
                        Toast.makeText(getApplicationContext(), R.string.TangoError,
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onTangoEvent(final TangoEvent event) {
                if(mTangoUx!=null){
                    mTangoUx.onTangoEvent(event);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //mTangoEventTextView.setText(event.eventKey + ": " + event.eventValue);
                    }
                });
            }

            @Override
            public void onFrameAvailable(int cameraId) {
                // We are not using onFrameAvailable for this application.
                if (cameraId == TangoCameraIntrinsics.TANGO_CAMERA_COLOR) {
                    mCameraView.onFrameAvailable();
                    Bitmap bitmap = mCameraView.getDrawingCache();
                    double timestamp = mCameraView.getTimestamp() * 1000;

                    synchronized (imageLock) {


                    }
                }
            }
        });
    }

    private float[] transformPose(TangoPoseData pose_start_service_T_device) {
        float[] rotation_start_service_T_device = pose_start_service_T_device.getRotationAsFloats();
        float[] translation_start_service_T_device = pose_start_service_T_device.getTranslationAsFloats();

        float[] start_service_T_device = new float[16];
        Matrix.setIdentityM(start_service_T_device, 0);
        start_service_T_device = ModelMatCalculator.quaternionMatrixOpenGL(rotation_start_service_T_device);
        start_service_T_device[12] += translation_start_service_T_device[0];
        start_service_T_device[13] += translation_start_service_T_device[1];
        start_service_T_device[14] += translation_start_service_T_device[2];

        float[] start_service_T_depth = new float[16];
        Matrix.setIdentityM(start_service_T_depth, 0);
        Matrix.multiplyMM(start_service_T_depth, 0, start_service_T_device, 0, device_T_depth, 0);

        float tr = start_service_T_depth[0] + start_service_T_depth[5] + start_service_T_depth[10];

        float qw, qx, qy, qz;

        if (tr > 0) {
            float s = (float) Math.sqrt(1 + tr) * 2;
            qw = (float) (0.25 * s);
            qx = (start_service_T_depth[6] - start_service_T_depth[9]) / s;
            qy = (start_service_T_depth[8] - start_service_T_depth[2]) / s;
            qz = (start_service_T_depth[1] - start_service_T_depth[4]) / s;

        // check which of the diagonal entries is the largest and use it for calculation
        // Qxx is largest
        } else if (start_service_T_depth[0] > start_service_T_depth[5] &&
                start_service_T_depth[0] > start_service_T_depth[10]){
            Log.e("TSXX", String.valueOf(start_service_T_device[0]));
            float s = (float) Math.sqrt(1 + start_service_T_depth[0] -
                    start_service_T_depth[5] - start_service_T_depth[10]) * 2;
            qw = (start_service_T_depth[6] - start_service_T_depth[9]) / s;
            qx = (float) (0.25 * s);
            qy = (start_service_T_depth[4] + start_service_T_depth[1]) / s;
            qz = (start_service_T_depth[8] + start_service_T_depth[2]) / s;

        }
        // Qyy is largest
        else if (start_service_T_depth[5] > start_service_T_depth[10]){
            Log.e("TSYY", String.valueOf(start_service_T_device[5]));
            float s = (float) Math.sqrt(1 + start_service_T_depth[5] -
                    start_service_T_depth[0] - start_service_T_depth[10]) * 2;
            qw = (start_service_T_depth[8] - start_service_T_depth[2]) / s;
            qx = (start_service_T_depth[4] + start_service_T_depth[1]) / s;
            qy = (float) (0.25 * s);
            qz = (start_service_T_depth[9] + start_service_T_depth[6]) / s;
        }
        // Qzz is largest
        else {
            Log.e("TSZZ", String.valueOf(start_service_T_device[10]));
            float s = (float) Math.sqrt(1 + start_service_T_depth[10] -
                    start_service_T_depth[0] - start_service_T_depth[5]) * 2;
            qw = (start_service_T_depth[1] - start_service_T_depth[4]) * s;
            qx = (start_service_T_depth[8] + start_service_T_depth[2]) * s;
            qy = (start_service_T_depth[9] + start_service_T_depth[6]) * s;
            qz = (float) (0.25 * s);
        }

        // SS_T_DEVICE for testing


       /* float[] depth_T_device = new float[16];
        Matrix.setIdentityM(depth_T_device, 0);
        Matrix.invertM(depth_T_device, 0, device_T_depth, 0);

        float[] start_service_T_device_test = new float[16];
        Matrix.setIdentityM(start_service_T_device_test, 0);
        Matrix.multiplyMM(start_service_T_device_test, 0, start_service_T_depth, 0, depth_T_device, 0);


        tr = start_service_T_device_test[0] + start_service_T_device_test[5] + start_service_T_device_test[10];
        if (tr > 0) {
            Log.e("TB", String.valueOf(tr));
            float s = (float) Math.sqrt(1 + tr) * 2;
            qw = (float) (0.25 * s);
            qx = (start_service_T_device_test[6] - start_service_T_device_test[9]) / s;
            qy = (start_service_T_device_test[8] - start_service_T_device_test[2]) / s;
            qz = (start_service_T_device_test[1] - start_service_T_device_test[4]) / s;

            // check which of the diagonal entries is the largest and use it for calculation
            // Qxx is largest
        } else if (start_service_T_device_test[0] > start_service_T_device_test[5] &&
                start_service_T_device_test[0] > start_service_T_device_test[10]){
            Log.e("TS", String.valueOf(start_service_T_device_test[0]));
            float s = (float) Math.sqrt(1 + start_service_T_device_test[0] -
                    start_service_T_device_test[5] - start_service_T_device_test[10]) * 2;
            qw = (start_service_T_device_test[6] - start_service_T_device_test[9]) / s;
            qx = (float) (0.25 * s);
            qy = (start_service_T_device_test[4] + start_service_T_device_test[1]) / s;
            qz = (start_service_T_device_test[8] + start_service_T_device_test[2]) / s;

        }
        // Qyy is largest
        else if (start_service_T_device_test[5] > start_service_T_device_test[10]){
            Log.e("TS", String.valueOf(start_service_T_device_test[5]));
            float s = (float) Math.sqrt(1 + start_service_T_device_test[5] -
                    start_service_T_device_test[0] - start_service_T_device_test[10]) * 2;
            qw = (start_service_T_device_test[8] - start_service_T_device_test[2]) / s;
            qx = (start_service_T_device_test[4] + start_service_T_device_test[1]) / s;
            qy = (float) (0.25 * s);
            qz = (start_service_T_device_test[9] + start_service_T_device_test[6]) / s;
        }
        // Qzz is largest
        else {
            Log.e("TS", String.valueOf(start_service_T_device_test[10]));
            float s = (float) Math.sqrt(1 + start_service_T_device_test[10] -
                    start_service_T_device_test[0] - start_service_T_device_test[5]) * 2;
            qw = (start_service_T_device_test[1] - start_service_T_device_test[4]) * s;
            qx = (start_service_T_device_test[8] + start_service_T_device_test[2]) * s;
            qy = (start_service_T_device_test[9] + start_service_T_device_test[6]) * s;
            qz = (float) (0.25 * s);
        }

        Log.e("START2DEVICETEST", "Rotation");
        Log.e("QW", String.valueOf(qw));
        Log.e("QX", String.valueOf(qx));
        Log.e("QY", String.valueOf(qy));
        Log.e("QZ", String.valueOf(qz));

        Log.e("START2DEVICETEST", "Translation");
        Log.e("TX", String.valueOf(start_service_T_device_test[12]));
        Log.e("TY", String.valueOf(start_service_T_device_test[13]));
        Log.e("TZ", String.valueOf(start_service_T_device_test[14]));*/

        float[] quaternion = new float[7];
        quaternion[0] = start_service_T_depth[12]; //tx
        quaternion[1] = start_service_T_depth[13]; //ty
        quaternion[2] = start_service_T_depth[14]; //tz
        quaternion[3] = qw; //rw
        quaternion[4] = qx; //rx
        quaternion[5] = qy; //ry
        quaternion[6] = qz; //rz

        return quaternion;
    }

    private float[] copyXyz(FloatBuffer xyz) {
        float[] newValues = new float[xyz.capacity()];

        float[] transformedPoint = new float[4];
        float[] point = new float[4];

        for (int i = 0; i < xyz.capacity() - 3; i = i + 3)
        {
            point[0] = xyz.get(i);
            point[1] = xyz.get(i+1);
            point[2] = xyz.get(i+2);
            point[3] = 1;

            Matrix.multiplyMV(transformedPoint, 0, device_T_depth, 0, point, 0);

            newValues[i] = transformedPoint[0];
            newValues[i+1] = transformedPoint[1];
            newValues[i+2] = transformedPoint[2];

            /*newValues[i] = point[0];
            newValues[i+1] = point[1];
            newValues[i+2] = point[2];*/
        }

        return newValues;
    }

    private float[] copyXyz2(FloatBuffer xyz) {
        float[] newValues = new float[xyz.capacity()];

        float[] transformedPoint = new float[4];
        float[] point = new float[4];

        for (int i = 0; i < xyz.capacity() - 3; i = i + 3)
        {
            point[0] = xyz.get(i);
            point[1] = xyz.get(i+1);
            point[2] = xyz.get(i+2);
            point[3] = 1;

            /*Matrix.multiplyMV(transformedPoint, 0, mcam2dev_Transform, 0, point, 0);

            newValues[i] = transformedPoint[0];
            newValues[i+1] = transformedPoint[1];
            newValues[i+2] = transformedPoint[2];*/

            newValues[i] = point[0];
            newValues[i+1] = point[1];
            newValues[i+2] = point[2];
        }

        return newValues;
    }

    /**
     * Create a separate thread to update Log information on UI at the specified interval of
     * UPDATE_INTERVAL_MS. This function also makes sure to have access to the mPose atomically.
     */
    private void startUIThread() {
        new Thread(new Runnable() {
            final DecimalFormat threeDec = new DecimalFormat("0.000");

            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(UPDATE_INTERVAL_MS);
                        // Update the UI with TangoPose information
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                synchronized (poseLock) {
                                    if (mPoses == null) {
                                        return;
                                    }
                                    updateTextViews();
                                }
                                synchronized (depthLock) {
                                    // Display number of points in the point cloud
                                    //mPointCountTextView.setText(Integer.toString(mPointCount));
                                    mSavedPCLFilesTextView.setText("Buffered PCL Files: " + Integer.toString(mPclFileCounter));
                                }
                            }
                        });
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }


    private void updateTextViews() {
        if (mPoses[0] != null
                && mPoses[0].baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
                && mPoses[0].targetFrame == TangoPoseData.COORDINATE_FRAME_DEVICE) {
            mAdf2DeviceTranslationTextView.setText(getTranslationString(mPoses[0]));
            mAdf2DeviceQuatTextView.setText(getQuaternionString(mPoses[0]));
            mAdf2DevicePoseStatusTextView.setText(getPoseStatus(mPoses[0]));
            mAdf2DevicePoseCountTextView.setText(Integer.toString(mAdf2DevicePoseCount));
        }

        if (mPoses[1] != null
                && mPoses[1].baseFrame == TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE
                && mPoses[1].targetFrame == TangoPoseData.COORDINATE_FRAME_DEVICE) {
            mStart2DeviceTranslationTextView.setText(getTranslationString(mPoses[1]));
            mStart2DeviceQuatTextView.setText(getQuaternionString(mPoses[1]));
            mStart2DevicePoseStatusTextView.setText(getPoseStatus(mPoses[1]));
            mStart2DevicePoseCountTextView.setText(Integer.toString(mStart2DevicePoseCount));
        }

        if (mPoses[2] != null
                && mPoses[2].baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
                && mPoses[2].targetFrame == TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE) {
            mAdf2StartTranslationTextView.setText(getTranslationString(mPoses[2]));
            mAdf2StartQuatTextView.setText(getQuaternionString(mPoses[2]));
            mAdf2StartPoseStatusTextView.setText(getPoseStatus(mPoses[2]));
            mAdf2StartPoseCountTextView.setText(Integer.toString(mAdf2StartPoseCount));
        }
    }

    private String getTranslationString(TangoPoseData pose) {
        return "[" + threeDec.format(pose.translation[0]) + ","
                + threeDec.format(pose.translation[1]) + "," + threeDec.format(pose.translation[2])
                + "] ";

    }

    private String getQuaternionString(TangoPoseData pose) {
        return "[" + threeDec.format(pose.rotation[0]) + "," + threeDec.format(pose.rotation[1])
                + "," + threeDec.format(pose.rotation[2]) + "," + threeDec.format(pose.rotation[3])
                + "] ";

    }

    private String getPoseStatus(TangoPoseData pose) {
        switch (pose.statusCode) {
            case TangoPoseData.POSE_INITIALIZING:
                return getString(R.string.pose_initializing);
            case TangoPoseData.POSE_INVALID:
                return getString(R.string.pose_invalid);
            case TangoPoseData.POSE_VALID:
                return getString(R.string.pose_valid);
            default:
                return getString(R.string.pose_unknown);
        }
    }

}