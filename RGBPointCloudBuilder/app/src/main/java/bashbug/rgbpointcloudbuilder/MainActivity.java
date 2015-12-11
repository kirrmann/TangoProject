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

package bashbug.rgbpointcloudbuilder;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Point;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import java.io.File;

/**
 * Activity that load up the main screen of the app, this is the launcher activity.
 */
public class MainActivity extends Activity implements View.OnClickListener {
    // Used for startActivityForResult on our motion tracking permission.
    private static final int REQUEST_PERMISSION_MOTION_TRACKING = 0;
    /// The input argument is invalid.
    private static final int  TANGO_INVALID = -2;
    /// This error code denotes some sort of hard error occurred.
    private static final int  TANGO_ERROR = -1;
    /// This code indicates success.
    private static final int  TANGO_SUCCESS = 0;

    // Motion Tracking permission request action.
    private static final String MOTION_TRACKING_PERMISSION_ACTION =
            "android.intent.action.REQUEST_TANGO_PERMISSION";

    // Key string for requesting and checking Motion Tracking permission.
    private static final String MOTION_TRACKING_PERMISSION =
            "MOTION_TRACKING_PERMISSION";

    private GLSurfaceRenderer mRenderer;
    private GLSurfaceView mGLView;

    private CheckBox mRGBMapCheckbox;
    private CheckBox mDepthMapCheckbox;

    private Switch mStartPCDRecordingSwitch;
    private Button mSendPCDContainerButton;
    private Switch mSendPCDSwitch;
    private Switch mSavePCDSwitch;
    private Button mSaveImage;

    private Point mScreenSize;

    File mFileDirectionPCD, mFileDirectionPPM;

    private boolean mIsConnectedService = false;

    private static final String TAG = "RGBDepthSync";

    private class GPUUpsampleListener implements CheckBox.OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            JNIInterface.setDepthMap(isChecked);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        Display display = getWindowManager().getDefaultDisplay();
        mScreenSize = new Point();
        display.getSize(mScreenSize);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        int status = JNIInterface.tangoInitialize(this);
        if (status != TANGO_SUCCESS) {
          if (status == TANGO_INVALID) {
            Toast.makeText(this, 
              "Tango Service version mis-match", Toast.LENGTH_SHORT).show();
          } else {
            Toast.makeText(this, 
              "Tango Service initialize internal error",
              Toast.LENGTH_SHORT).show();
          }
        }
        setContentView(R.layout.activity_main);
        
        mRGBMapCheckbox = (CheckBox) findViewById(R.id.rgb_map_checkbox);
        mRGBMapCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton view, boolean isChecked) {
            if (isChecked) {
                mDepthMapCheckbox.setChecked(false);
                JNIInterface.setDepthMap(false);
            }
            JNIInterface.setRGBMap(isChecked);
            }
        });

        mDepthMapCheckbox = (CheckBox) findViewById(R.id.depth_map_checkbox);
        mDepthMapCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton view, boolean isChecked) {
            if (isChecked) {
                mRGBMapCheckbox.setChecked(false);
                JNIInterface.setRGBMap(false);
            }
            JNIInterface.setDepthMap(isChecked);
            }
        });

        // OpenGL view where all of the graphics are drawn
        mGLView = (GLSurfaceView) findViewById(R.id.gl_surface_view);

        // Configure OpenGL renderer
        mGLView.setEGLContextClientVersion(2);
        mRenderer = new GLSurfaceRenderer(this);
        mGLView.setRenderer(mRenderer);


        // Send pcl to file buttons
        mSendPCDSwitch = (Switch) findViewById(R.id.send_pcl_switch);
        mSendPCDSwitch.setEnabled(false);
        mSendPCDSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                JNIInterface.setPCDSend(isChecked);
            }
        });

        // Save pcl to file buttons
        mSavePCDSwitch = (Switch) findViewById(R.id.save_pcl_switch);
        mSavePCDSwitch.setActivated(false);
        mSavePCDSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                JNIInterface.setPCDSave(isChecked);
            }
        });

        mStartPCDRecordingSwitch = (Switch) findViewById(R.id.start_point_cloud_container_switch);
        mStartPCDRecordingSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                JNIInterface.setStartPCDRecording(isChecked);
            }
        });

        mSendPCDContainerButton = (Button) findViewById(R.id.send_point_cloud_container_button);
        mSendPCDContainerButton.setOnClickListener(this);

        mSaveImage = (Button) findViewById(R.id.save_image);
        mSaveImage.setOnClickListener(this);

        // Buttons for selecting camera view and Set up button click listeners.
        findViewById(R.id.first_person_button).setOnClickListener(this);
        findViewById(R.id.third_person_button).setOnClickListener(this);
        findViewById(R.id.top_down_button).setOnClickListener(this);

        // Make sure that the directories exists before saving files. Otherwise it will
        // throw an exception
        mFileDirectionPCD = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "./RGBPointCloudBuilder/PCD");

        mFileDirectionPPM = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "./RGBPointCloudBuilder/PPM");

        if(!mFileDirectionPCD.isDirectory()) {
            mFileDirectionPCD.mkdirs();
            Log.e(TAG, "RGBPointCloudBuilder/PCD Directory not exists");
        }
        if(!mFileDirectionPPM.isDirectory()) {
            mFileDirectionPPM.mkdirs();
            Log.e(TAG, "RGBPointCloudBuilder/PPM Directory not exists");
        }
        if (!mFileDirectionPCD.isDirectory()) {
            Log.e(TAG, "RGBPointCloudBuilder/PPM Directory not created");
        }
        if (!mFileDirectionPPM.isDirectory()) {
            Log.e(TAG, "RGBPointCloudBuilder/PPM Directory not created");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.ic_file_upload:
                showSetServerSocketAddresssAndIPDialog();
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.save_image:
                JNIInterface.storeImage(true);
                break;
            case R.id.send_point_cloud_container_button:
                JNIInterface.setSendPCDContainer(true);
                break;
            case R.id.first_person_button:
                JNIInterface.setCamera(0);
                break;
            case R.id.third_person_button:
                JNIInterface.setCamera(1);
                break;
            case R.id.top_down_button:
                JNIInterface.setCamera(2);
                break;

            default:
                Log.w(TAG, "Unrecognized button click.");
                return;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Pass the touch event to the native layer for camera control.
        // Single touch to rotate the camera around the device.
        // Two fingers to zoom in and out.
        int pointCount = event.getPointerCount();
        if (pointCount == 1) {
            float normalizedX = event.getX(0) / mScreenSize.x;
            float normalizedY = event.getY(0) / mScreenSize.y;
            JNIInterface.onTouchEvent(1,
                    event.getActionMasked(), normalizedX, normalizedY, 0.0f, 0.0f);
        }
        if (pointCount == 2) {
            if (event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
                int index = event.getActionIndex() == 0 ? 1 : 0;
                float normalizedX = event.getX(index) / mScreenSize.x;
                float normalizedY = event.getY(index) / mScreenSize.y;
                JNIInterface.onTouchEvent(1,
                        MotionEvent.ACTION_DOWN, normalizedX, normalizedY, 0.0f, 0.0f);
            } else {
                float normalizedX0 = event.getX(0) / mScreenSize.x;
                float normalizedY0 = event.getY(0) / mScreenSize.y;
                float normalizedX1 = event.getX(1) / mScreenSize.x;
                float normalizedY1 = event.getY(1) / mScreenSize.y;
                JNIInterface.onTouchEvent(2, event.getActionMasked(),
                        normalizedX0, normalizedY0, normalizedX1, normalizedY1);
            }
        }
        return true;
    }


    @Override
    protected void onResume() {
        // We moved most of the onResume lifecycle calls to the surfaceCreated,
        // surfaceCreated will be called after the GLSurface is created.
        super.onResume();

        // Though we're going to use Tango's C interface so that we have more
        // low level control of our graphics, we can still use the Java API to
        // check that we have the correct permissions.
        if (!hasPermission(this, MOTION_TRACKING_PERMISSION)) {
            getMotionTrackingPermission();
        } else {
            mGLView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mGLView.onPause();
        if (mIsConnectedService) {
            JNIInterface.tangoDisconnect();
        }
        JNIInterface.freeGLContent();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public void surfaceCreated() {
        JNIInterface.initializeGLContent();
        int ret = JNIInterface.tangoConnectTexture();
        if (ret != TANGO_SUCCESS) {
            Log.e(TAG, "Failed to connect texture with code: "  + ret);
            finish();
        }

        ret = JNIInterface.tangoSetupConfig();
        if (ret != TANGO_SUCCESS) {
            Log.e(TAG, "Failed to set config with code: "  + ret);
            finish();
        }

        ret = JNIInterface.tangoConnectCallbacks();
        if (ret != TANGO_SUCCESS) {
            Log.e(TAG, "Failed to set connect cbs with code: "  + ret);
            finish();
        }

        ret = JNIInterface.tangoConnect();
        if (ret != TANGO_SUCCESS) {
            Log.e(TAG, "Failed to set connect service with code: "  + ret);
            finish();
        }

        ret = JNIInterface.tangoSetIntrinsicsAndExtrinsics();
        if (ret != TANGO_SUCCESS) {
            Log.e(TAG, "Failed to extrinsics and intrinsics code: "  + ret);
            finish();
        }

        mIsConnectedService = true;
    }

    @Override
    protected void onActivityResult (int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PERMISSION_MOTION_TRACKING) {
            if (resultCode == RESULT_CANCELED) {
                mIsConnectedService = false;
                finish();
            }
        }
    }

    public boolean hasPermission(Context context, String permissionType){
        Uri uri = Uri.parse("content://com.google.atap.tango.PermissionStatusProvider/" +
                permissionType);
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        if (cursor == null) {
            return false;
        } else {
            return true;
        }
    }

    // Call the permission intent for the Tango Service to ask for motion tracking
    // permissions. All permission types can be found here:
    //   https://developers.google.com/project-tango/apis/c/c-user-permissions
    private void getMotionTrackingPermission() {
        Intent intent = new Intent();
        intent.setAction(MOTION_TRACKING_PERMISSION_ACTION);
        intent.putExtra("PERMISSIONTYPE", MOTION_TRACKING_PERMISSION);

        // After the permission activity is dismissed, we will receive a callback
        // function onActivityResult() with user's result.
        startActivityForResult(intent, 0);
    }

    private void showSetServerSocketAddresssAndIPDialog() {
        FragmentManager manager = getFragmentManager();
        SetServerSocketAddressAndIPDialog setServerSocketAddressAndIPDialog = new SetServerSocketAddressAndIPDialog();
        setServerSocketAddressAndIPDialog.show(manager, "SocketAddIPDialog");
    }

    public void setmSendPCDSwitch(boolean on) {
        mSendPCDSwitch.setEnabled(on);
    }
}
