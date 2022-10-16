package com.baidu.paddle.lite.demo.object_detection;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.*;
import android.widget.*;

import com.baidu.paddle.lite.demo.common.CameraSurfaceView;
import com.baidu.paddle.lite.demo.common.Utils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends Activity implements View.OnClickListener, CameraSurfaceView.OnTextureChangedListener {
    private static final String TAG = MainActivity.class.getSimpleName();

    CameraSurfaceView svPreview;
    TextView tvStatus;
    ImageButton btnSwitch;
    ImageButton btnShutter;
    ImageButton btnSettings;

    String savedImagePath = "result.jpg";
    int lastFrameIndex = 0;
    long lastFrameTime;

    Native predictor = new Native();

    private TextView actionTakePictureTv;
    private TextView actionRealtimeTv;
    private ImageView takePictureButton;
    private ImageView albumSelectButton;
    private ImageView realtimeToggleButton;
    boolean isRealtimeStatusRunning = false;
    protected boolean canAutoRun = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);

        // Clear all setting items to avoid app crashing due to the incorrect settings
        initSettings();

        // Init the camera preview and UI components
        initView();

        // Check and request CAMERA and WRITE_EXTERNAL_STORAGE permissions
        if (!checkAllPermissions()) {
            requestAllPermissions();
        }
        canAutoRun = true;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_switch:
                svPreview.switchCamera();
                break;
            case R.id.btn_shutter:
                SimpleDateFormat date = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
                synchronized (this) {
                    savedImagePath = Utils.getDCIMDirectory() + File.separator + date.format(new Date()).toString() + ".png";
                }
                Toast.makeText(MainActivity.this, "Save snapshot to " + savedImagePath, Toast.LENGTH_SHORT).show();
                break;
            case R.id.btn_settings:
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                break;
        }
    }

    @Override
    public boolean onTextureChanged(Bitmap ARGB8888ImageBitmap) {
        String savedImagePath = "";
        synchronized (this) {
            savedImagePath = MainActivity.this.savedImagePath;
        }
        boolean modified = predictor.process(ARGB8888ImageBitmap, savedImagePath);
        if (!savedImagePath.isEmpty()) {
            synchronized (this) {
                MainActivity.this.savedImagePath = "result.jpg";
            }
        }
        lastFrameIndex++;
        if (lastFrameIndex >= 30) {
            final int fps = (int) (lastFrameIndex * 1e9 / (System.nanoTime() - lastFrameTime));
            runOnUiThread(new Runnable() {
                public void run() {
                    tvStatus.setText(Integer.toString(fps) + "fps");
                }
            });
            lastFrameIndex = 0;
            lastFrameTime = System.nanoTime();
        }
        return modified;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload settings and re-initialize the predictor
        checkAndUpdateSettings();
        // Open camera until the permissions have been granted
        if (!checkAllPermissions()) {
            svPreview.disableCamera();
        }
        svPreview.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        svPreview.onPause();
    }

    @Override
    protected void onDestroy() {
        if (predictor != null) {
            predictor.release();
        }
        super.onDestroy();
        isRealtimeStatusRunning = false;
    }

    public void initView() {
        svPreview = (CameraSurfaceView) findViewById(R.id.sv_preview);
        svPreview.setOnTextureChangedListener(this);
        tvStatus = (TextView) findViewById(R.id.tv_status);
        btnSwitch = (ImageButton) findViewById(R.id.btn_switch);
        btnSwitch.setOnClickListener(this);
        btnShutter = (ImageButton) findViewById(R.id.btn_shutter);
        btnShutter.setOnClickListener(this);
        btnSettings = (ImageButton) findViewById(R.id.btn_settings);
        btnSettings.setOnClickListener(this);
        actionTakePictureTv = findViewById(R.id.action_takepicture_btn);
        actionRealtimeTv = findViewById(R.id.action_realtime_btn);
        actionTakePictureTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setActionBtnHighlight(actionTakePictureTv);
                setActionBtnDefault(actionRealtimeTv);
                toggleOperationBtns(true);
            }
        });
        actionRealtimeTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setActionBtnHighlight(actionRealtimeTv);
                setActionBtnDefault(actionTakePictureTv);
                toggleOperationBtns(false);
                toggleRealtimeStyle();
            }
        });
        takePictureButton = findViewById(R.id.btn_shutter);
        albumSelectButton = findViewById(R.id.albumSelect);
        setTakePictureButtonAvailable(true);
        realtimeToggleButton = findViewById(R.id.realtime_toggle_btn);
        takePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: 2022/10/15

            }
        });
        albumSelectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: 2022/10/15  
            }
        });
        realtimeToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: 2022/10/15
                toggleRealtimeStyle();
            }
        });
    }

    /**
     * 切换扫描或者拍照模式
     *
     * @param isTakePicture
     */
    private void toggleOperationBtns(boolean isTakePicture) {
        if (isTakePicture) {
            takePictureButton.setVisibility(View.VISIBLE);
            albumSelectButton.setVisibility(View.VISIBLE);
            realtimeToggleButton.setVisibility(View.GONE);
        } else {
            takePictureButton.setVisibility(View.INVISIBLE);
            albumSelectButton.setVisibility(View.GONE);
            realtimeToggleButton.setVisibility(View.VISIBLE);
        }
    }

    private void toggleRealtimeStatus() {
        if (canAutoRun || isRealtimeStatusRunning) {
            isRealtimeStatusRunning = !isRealtimeStatusRunning;
            toggleRealtimeStyle();
        }
    }

    private void toggleRealtimeStyle() {
        if (isRealtimeStatusRunning) {
            isRealtimeStatusRunning = false;
            realtimeToggleButton.setImageResource(R.drawable.realtime_stop_btn);
        } else {
            isRealtimeStatusRunning = true;
            realtimeToggleButton.setImageResource(R.drawable.realtime_start_btn);
        }
    }

    protected void setTakePictureButtonAvailable(boolean available) {
        if (takePictureButton == null) {
            return;
        }
        if (available) {
            takePictureButton.setColorFilter(null);
        } else {
            takePictureButton.setColorFilter(Color.GRAY);
        }
    }


    private void setActionBtnHighlight(TextView btn) {
        btn.setBackgroundResource(com.baidu.ai.edge.ui.R.color.bk_black);
        ColorStateList color = getResources().getColorStateList(com.baidu.ai.edge.ui.R.color.textColorHighlight);
        btn.setTextColor(color);
    }

    private void setActionBtnDefault(TextView btn) {
        btn.setBackgroundResource(com.baidu.ai.edge.ui.R.color.bk_black);
        ColorStateList color = getResources().getColorStateList(com.baidu.ai.edge.ui.R.color.textColor);
        btn.setTextColor(color);
    }

    @Override
    public void onBackPressed() {
        isRealtimeStatusRunning = false;
    }

    public void initSettings() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.commit();
        SettingsActivity.resetSettings();
    }

    public void checkAndUpdateSettings() {
        if (SettingsActivity.checkAndUpdateSettings(this)) {
            String realModelDir = getCacheDir() + "/" + SettingsActivity.modelDir;
            Utils.copyDirectoryFromAssets(this, SettingsActivity.modelDir, realModelDir);
            String realLabelPath = getCacheDir() + "/" + SettingsActivity.labelPath;
            Utils.copyFileFromAssets(this, SettingsActivity.labelPath, realLabelPath);
            predictor.init(
                    realModelDir,
                    realLabelPath,
                    SettingsActivity.cpuThreadNum,
                    SettingsActivity.cpuPowerMode,
                    SettingsActivity.inputWidth,
                    SettingsActivity.inputHeight,
                    SettingsActivity.inputMean,
                    SettingsActivity.inputStd,
                    SettingsActivity.scoreThreshold);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED || grantResults[1] != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Permission denied")
                    .setMessage("Click to force quit the app, then open Settings->Apps & notifications->Target " +
                            "App->Permissions to grant all of the permissions.")
                    .setCancelable(false)
                    .setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            MainActivity.this.finish();
                        }
                    }).show();
        }
    }

    private void requestAllPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA}, 0);
    }

    private boolean checkAllPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }
}
