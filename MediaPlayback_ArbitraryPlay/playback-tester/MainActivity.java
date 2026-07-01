package com.test.playback;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private EditText editTextUrl;
    private TextView statusText;
    private TextView logText;

    private static final String PKG = "com.huawei.music";
    private static final String ACTIVITY =
        "com.android.mediacenter.ui.player.oneshot.MediaPlaybackActivityStarter";

    private static final String[][] TEST_CASES = {
        {"file:///sdcard/Music/sample.mp3",
            "播放 file:// SD 卡音乐"},
        {"content://media/external/audio/media/",
            "播放 content:// 媒体库"},
        {"file:///system/media/audio/ringtones/",
            "播放 file:// 系统铃声目录"},
        {"content://com.android.contacts/contacts/1",
            "尝试 content:// contacts (非法)"},
        {"content://settings/secure/android_id",
            "尝试 content:// settings (非法)"},
        {"file:///data/data/com.android.providers.contacts/databases/contacts2.db",
            "尝试 file:// 私有数据库 (权限)"},
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        editTextUrl = findViewById(R.id.editTextUrl);
        statusText = findViewById(R.id.statusText);
        logText = findViewById(R.id.logText);
        logText.setMovementMethod(new ScrollingMovementMethod());

        log("MediaPlaybackActivityStarter Tester Ready");
        log("Target: " + PKG + "/" + ACTIVITY);
        log("");

        findViewById(R.id.btnSend).setOnClickListener(v -> {
            String url = editTextUrl.getText().toString().trim();
            if (!url.isEmpty()) triggerPlay(url);
            else Toast.makeText(this, "请输入 URI", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnCase1).setOnClickListener(v -> triggerPlay(TEST_CASES[0][0]));
        findViewById(R.id.btnCase2).setOnClickListener(v -> triggerPlay(TEST_CASES[1][0]));
        findViewById(R.id.btnCase3).setOnClickListener(v -> triggerPlay(TEST_CASES[3][0]));
        findViewById(R.id.btnCase4).setOnClickListener(v -> triggerPlay(TEST_CASES[4][0]));
    }

    private void triggerPlay(String uriStr) {
        log("");
        log("========================================");
        log("[+] Triggering MediaPlaybackActivityStarter...");
        log("    URI: " + uriStr);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(uriStr));
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setClassName(PKG, ACTIVITY);

        try {
            startActivity(intent);
            log("[+] startActivity() SUCCESS");
            log("[+] Intent sent to Huawei Music");
            log("[+] If music starts playing -> file/content URI is ACCESSIBLE");
            log("[+] If nothing happens -> protected / not an audio file");
            log("========================================");
            statusText.setText("✓ Intent 已发送！检查华为音乐是否播放");
            statusText.setTextColor(0xFF00AA00);
        } catch (Exception e) {
            log("[-] FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            log("========================================");
            statusText.setText("✗ 失败: " + e.getClass().getSimpleName());
            statusText.setTextColor(0xFFCC0000);
        }

        if (editTextUrl != null) {
            editTextUrl.setText(uriStr);
        }
    }

    private void log(String msg) {
        logText.append(msg + "\n");
        final int scrollAmount = logText.getLayout() != null
            ? logText.getLayout().getLineTop(logText.getLineCount()) : 0;
        if (scrollAmount > logText.getHeight()) {
            logText.scrollTo(0, scrollAmount - logText.getHeight());
        }
    }
}
