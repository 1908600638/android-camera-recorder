package com.camera;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.ResultReceiver;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.easydarwin.video.Client;
import org.easydarwin.video.EasyPlayerClient;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final int MODE_IP = 0;
    private static final int MODE_PHONE = 1;
    private int mode = MODE_IP;

    private TextureView rtspView;
    private EasyPlayerClient player;
    private String rtspUrl;
    private boolean rtspPlaying;

    private PreviewView previewView;
    private ProcessCameraProvider cameraProvider;
    private VideoCapture<Recorder> videoCapture;
    private Recording activeRecording;
    private int lensFacing = CameraSelector.LENS_FACING_BACK;
    private boolean phonePreviewOn;

    private boolean recording;

    private EditText ipInput;
    private EditText userInput;
    private EditText pwdInput;
    private Button btnMode;
    private Button btnSearch;
    private Button btnConnect;
    private Button btnDisconnect;
    private Button btnRecord;
    private Button btnSwitchCam;
    private TextView statusText;
    private View ipPanel;

    private static final String ACTION_SEARCH_RESULT = "com.a_s";
    private BroadcastReceiver searchReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        ipInput = findViewById(R.id.ip_input);
        userInput = findViewById(R.id.user_input);
        pwdInput = findViewById(R.id.pwd_input);
        btnMode = findViewById(R.id.btn_mode);
        btnSearch = findViewById(R.id.btn_search);
        btnConnect = findViewById(R.id.btn_connect);
        btnDisconnect = findViewById(R.id.btn_disconnect);
        btnRecord = findViewById(R.id.btn_record);
        btnSwitchCam = findViewById(R.id.btn_switch_cam);
        statusText = findViewById(R.id.status_text);
        ipPanel = findViewById(R.id.ip_panel);
        rtspView = findViewById(R.id.rtsp_view);
        previewView = findViewById(R.id.phone_view);

        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);

        btnMode.setOnClickListener(v -> switchMode());
        btnSearch.setOnClickListener(v -> searchCamera());
        btnConnect.setOnClickListener(v -> connectRtsp());
        btnDisconnect.setOnClickListener(v -> stopRtsp());
        btnRecord.setOnClickListener(v -> toggleRecord());
        btnSwitchCam.setOnClickListener(v -> switchLens());

        requestPermission();

        rtspView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture st, int w, int h) {
                Log.i(TAG, "TextureView 就绪");
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture st, int w, int h) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture st) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture st) {
            }
        });

        applyMode();
    }

    private void requestPermission() {
        String[] perms = {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, perms, 1001);
                return;
            }
        }
    }

    private void switchMode() {
        stopRecording();
        stopRtsp();
        stopPhoneCamera();

        mode = (mode == MODE_IP) ? MODE_PHONE : MODE_IP;
        applyMode();
    }

    private void applyMode() {
        boolean isIp = (mode == MODE_IP);

        ipPanel.setVisibility(isIp ? View.VISIBLE : View.GONE);
        rtspView.setVisibility(isIp ? View.VISIBLE : View.GONE);
        previewView.setVisibility(isIp ? View.GONE : View.VISIBLE);
        btnSwitchCam.setVisibility(isIp ? View.GONE : View.VISIBLE);

        btnMode.setText(isIp ? "切换到手机摄像头" : "切换到网络摄像头");

        if (isIp) {
            setStatus("网络摄像头：填写地址后点「连接」");
        } else {
            setStatus("手机摄像头");
            startPhoneCamera();
        }
    }

    private void searchCamera() {
        if (searchReceiver == null) {
            searchReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String pureIp = intent.getStringExtra("pureip");
                    String ipWithPort = intent.getStringExtra("IP");
                    Log.i(TAG, "搜索到摄像头: " + ipWithPort + " / " + pureIp);
                    if (pureIp != null && !pureIp.isEmpty()) {
                        ipInput.setText(pureIp);
                        setStatus("搜索到摄像头：" + pureIp);
                    }
                }
            };
            IntentFilter filter = new IntentFilter(ACTION_SEARCH_RESULT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(searchReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(searchReceiver, filter);
            }
        }
        startService(new Intent(this, CameraSearchService.class));
        setStatus("正在局域网搜索摄像头…");
    }

    private void connectRtsp() {
        String ip = ipInput.getText().toString().trim();
        String user = userInput.getText().toString().trim();
        String pwd = pwdInput.getText().toString().trim();

        if (ip.isEmpty()) {
            setStatus("请先填写摄像头 IP");
            return;
        }

        stopRtsp();

        if (!rtspView.isAvailable()) {
            setStatus("画面还没准备好，请稍后重试");
            return;
        }

        StringBuilder sb = new StringBuilder("rtsp://");
        if (!user.isEmpty() && !pwd.isEmpty()) {
            sb.append(user).append(':').append(pwd).append('@');
        }
        sb.append(ip).append(":10554/tcp/av0_0");
        rtspUrl = sb.toString();

        Log.i(TAG, "RTSP: " + rtspUrl.replace(pwd.isEmpty() ? "" : pwd, "******"));

        ResultReceiver receiver = new ResultReceiver(new Handler()) {
            @Override
            protected void onReceiveResult(int code, Bundle data) {
                if (code == EasyPlayerClient.RESULT_VIDEO_DISPLAYED) {
                    setStatus("正在播放");
                } else if (code == EasyPlayerClient.RESULT_EVENT) {
                    setStatus("播放出错，请检查地址、账号密码或网络");
                }
            }
        };

        final EasyPlayerClient client = new EasyPlayerClient(
                this, "EasyPlayer is free!", rtspView,
                new Surface(rtspView.getSurfaceTexture()), receiver);
        player = client;
        player.setAudioEnable(false);

        new Thread(() -> {
            if (player != client) {
                return;
            }
            int result = client.start(rtspUrl, Client.TRANSTYPE_TCP, 1,
                    Client.EASY_SDK_VIDEO_FRAME_FLAG, "", "", null);
            Log.i(TAG, "EasyPlayerClient.start 返回 " + result);
            if (result != 0 && result != 500) {
                runOnUiThread(() -> setStatus("连接失败，请检查摄像头 IP 与网络"));
            } else {
                rtspPlaying = true;
                runOnUiThread(() -> setStatus("已连接，等待画面…"));
            }
        }, "rtsp-connect").start();
    }

    private void stopRtsp() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception e) {
                Log.w(TAG, "停止播放异常", e);
            }
            player = null;
        }
        rtspPlaying = false;
        recording = false;
        btnRecord.setText("开始录像");
    }

    private void startPhoneCamera() {
        if (phonePreviewOn) {
            return;
        }
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindPhoneCamera();
                phonePreviewOn = true;
            } catch (Exception e) {
                Log.e(TAG, "相机初始化失败", e);
                setStatus("相机初始化失败：" + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPhoneCamera() {
        if (cameraProvider == null) {
            return;
        }
        cameraProvider.unbindAll();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        Recorder recorder = new Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build();
        videoCapture = VideoCapture.withOutput(recorder);

        CameraSelector selector = new CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build();

        cameraProvider.bindToLifecycle(this, selector, preview, videoCapture);
    }

    private void switchLens() {
        if (mode != MODE_PHONE) {
            return;
        }
        stopRecording();
        lensFacing = (lensFacing == CameraSelector.LENS_FACING_BACK)
                ? CameraSelector.LENS_FACING_FRONT
                : CameraSelector.LENS_FACING_BACK;
        bindPhoneCamera();
        setStatus(lensFacing == CameraSelector.LENS_FACING_BACK ? "后置摄像头" : "前置摄像头");
    }

    private void stopPhoneCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        phonePreviewOn = false;
    }

    private void toggleRecord() {
        if (recording) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        File dir = getVideoDir();
        if (dir == null) {
            setStatus("找不到可写的存储目录");
            return;
        }
        File file = new File(dir, "VID_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date()) + ".mp4");

        if (mode == MODE_IP) {
            if (player == null || !rtspPlaying) {
                setStatus("还没有画面，请先连接摄像头");
                return;
            }
            try {
                player.startRecord(file.getAbsolutePath());
                if (player.isRecording()) {
                    recording = true;
                    btnRecord.setText("停止录像");
                    setStatus("录像中：" + file.getName());
                } else {
                    setStatus("录像启动失败，请等画面稳定后重试");
                }
            } catch (Exception e) {
                Log.e(TAG, "RTSP 录像失败", e);
                setStatus("录像失败：" + e.getMessage());
            }
            return;
        }

        if (videoCapture == null) {
            setStatus("相机还没准备好");
            return;
        }
        try {
            FileOutputOptions options = new FileOutputOptions.Builder(file).build();
            PendingRecording pending = videoCapture.getOutput()
                    .prepareRecording(this, options);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                pending = pending.withAudioEnabled();
            }
            activeRecording = pending.start(ContextCompat.getMainExecutor(this), event -> {
                        if (event instanceof VideoRecordEvent.Finalize) {
                            VideoRecordEvent.Finalize fin = (VideoRecordEvent.Finalize) event;
                            if (fin.hasError()) {
                                setStatus("录像结束，错误码 " + fin.getError());
                            } else {
                                setStatus("已保存：" + file.getName());
                            }
                            recording = false;
                            btnRecord.setText("开始录像");
                        }
                    });
            recording = true;
            btnRecord.setText("停止录像");
            setStatus("录像中：" + file.getName());
        } catch (Exception e) {
            Log.e(TAG, "手机录像失败", e);
            setStatus("录像失败：" + e.getMessage());
        }
    }

    private void stopRecording() {
        if (!recording) {
            return;
        }
        if (mode == MODE_IP) {
            if (player != null && player.isRecording()) {
                player.stopRecord();
            }
        } else if (activeRecording != null) {
            try {
                activeRecording.stop();
            } catch (Exception e) {
                Log.w(TAG, "停止录像异常", e);
            }
            activeRecording = null;
        }
        recording = false;
        btnRecord.setText("开始录像");
    }

    private File getVideoDir() {
        File dir = null;
        try {
            boolean hasAll;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                hasAll = Environment.isExternalStorageManager();
            } else {
                hasAll = true;
            }
            if (hasAll) {
                dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_MOVIES), "Camera");
            }
        } catch (Exception e) {
            Log.w(TAG, "公共目录不可用", e);
        }
        if (dir == null || (!dir.exists() && !dir.mkdirs())) {
            dir = new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Camera");
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
        }
        return dir;
    }

    private void setStatus(String text) {
        statusText.setText(text);
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
        Log.i(TAG, text);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRecording();
        stopRtsp();
        stopPhoneCamera();
        if (searchReceiver != null) {
            try {
                unregisterReceiver(searchReceiver);
            } catch (Exception ignored) {
            }
            searchReceiver = null;
        }
        stopService(new Intent(this, CameraSearchService.class));
    }
}
