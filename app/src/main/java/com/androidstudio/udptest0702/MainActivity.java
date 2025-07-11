package com.androidstudio.udptest0702;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.SuperscriptSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.graphics.Color;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetAddress;

public class MainActivity extends AppCompatActivity {

    // 健康数据视图
    private TextView tvHeartRate, tvBloodOxygen, tvHeartDisease;
    private TextView tvEEG1, tvEEG2, tvEEG3, tvEEG4;
    private TextView tvEpilepsyRisk, tvSleepQuality;
    private TextView tvStatus; // 状态指示器

    // 控制按钮
    private Button btnStartServer, btnStopServer;

    // 服务器相关
    private UdpServer udpServer;
    private final int PORT = 8888;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    // 定时刷新任务
    private Runnable refreshTask;
    private static final int REFRESH_INTERVAL = 1000; // 1秒刷新一次
    private long lastDataTime = 0; // 最后接收数据的时间戳
    private static final long CONNECTION_TIMEOUT = 5000; // 5秒超时

    // 自定义Span用于垂直偏移
    private static class BaselineSpan extends MetricAffectingSpan {
        @Override
        public void updateDrawState(TextPaint tp) {
            tp.baselineShift += (int) (tp.ascent() * 0.3); // 增加30%的偏移
        }

        @Override
        public void updateMeasureState(TextPaint tp) {
            tp.baselineShift += (int) (tp.ascent() * 0.3); // 增加30%的偏移
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化所有健康数据视图
        initHealthDataViews();

        // 初始化控制按钮
        initControlButtons();

        // 初始化定时刷新任务
        initRefreshTask();
    }

    private void initHealthDataViews() {
        tvHeartRate = findViewById(R.id.tv_heart_rate);
        tvBloodOxygen = findViewById(R.id.tv_blood_oxygen);
        tvHeartDisease = findViewById(R.id.tv_heart_disease);
        tvEEG1 = findViewById(R.id.tv_eeg1);
        tvEEG2 = findViewById(R.id.tv_eeg2);
        tvEEG3 = findViewById(R.id.tv_eeg3);
        tvEEG4 = findViewById(R.id.tv_eeg4);
        tvEpilepsyRisk = findViewById(R.id.tv_epilepsy_risk);
        tvSleepQuality = findViewById(R.id.tv_sleep_quality);
        tvStatus = findViewById(R.id.tv_status); // 状态指示器

        // 设置初始值
        tvHeartRate.setText("-- BPM");
        tvBloodOxygen.setText("-- %");
        tvHeartDisease.setText("检测中...");
        setEegText(tvEEG1, "--");
        setEegText(tvEEG2, "--");
        setEegText(tvEEG3, "--");
        setEegText(tvEEG4, "--");
        tvEpilepsyRisk.setText("检测中...");
        tvSleepQuality.setText("--");
        tvStatus.setText("状态: 未连接");
        tvStatus.setTextColor(Color.GRAY);
    }

    private void initControlButtons() {
        btnStartServer = findViewById(R.id.btn_start_server);
        btnStopServer = findViewById(R.id.btn_stop_server);

        // 开始服务端监听
        btnStartServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 重置连接状态
                lastDataTime = 0;
                tvStatus.setText("状态: 启动中...");
                tvStatus.setTextColor(Color.BLUE);

                if (udpServer != null) {
                    udpServer.stopServer();
                }

                udpServer = new UdpServer(PORT);
                udpServer.setOnUdpReceiveListener(new UdpServer.OnUdpReceiveListener() {
                    @Override
                    public void onUdpReceive(final String data,
                                             final InetAddress fromAddress,
                                             final int fromPort) {
                        // 记录最后接收时间
                        lastDataTime = System.currentTimeMillis();

                        // 更新状态
                        mHandler.post(() -> {
                            String ip = fromAddress.getHostAddress();
                            tvStatus.setText("状态: 已连接 (" + ip + ":" + fromPort + ")");
                            tvStatus.setTextColor(Color.GREEN);
                        });

                        // 解析健康数据
                        parseHealthData(data);
                    }
                });
                udpServer.startServer();

                // 启动定时刷新
                mHandler.postDelayed(refreshTask, REFRESH_INTERVAL);
            }
        });

        // 停止服务端
        btnStopServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (udpServer != null) {
                    udpServer.stopServer();
                    udpServer = null;
                }

                // 更新状态
                mHandler.post(() -> {
                    tvStatus.setText("状态: 已停止");
                    tvStatus.setTextColor(Color.GRAY);
                });

                // 停止定时刷新
                mHandler.removeCallbacks(refreshTask);
            }
        });
    }

    private void initRefreshTask() {
        refreshTask = new Runnable() {
            @Override
            public void run() {
                // 检查连接状态
                long currentTime = System.currentTimeMillis();
                if (lastDataTime > 0 && (currentTime - lastDataTime) > CONNECTION_TIMEOUT) {
                    mHandler.post(() -> {
                        tvStatus.setText("状态: 连接中断");
                        tvStatus.setTextColor(Color.RED);
                    });
                }

                // 每秒刷新一次
                mHandler.postDelayed(this, REFRESH_INTERVAL);
            }
        };
    }

    private void parseHealthData(String data) {
        Log.d("UDP", "收到数据: " + data);

        try {
            JSONObject jsonData = new JSONObject(data);

            // 1. 解析心率和血氧
            JSONArray heartArray = jsonData.getJSONArray("heart");
            double heartRateValue = heartArray.getDouble(0);
            double bloodOxygenValue = heartArray.getDouble(1);

            // 2. 解析脑电波
            JSONArray eegArray = jsonData.getJSONArray("eeg");
            double eeg1 = eegArray.getDouble(0);
            double eeg2 = eegArray.getDouble(1);
            double eeg3 = eegArray.getDouble(2);
            double eeg4 = eegArray.getDouble(3);

            // 3. 解析输出结果
            JSONObject output = jsonData.getJSONObject("output");
            double heartRisk = output.getDouble("heart");
            JSONArray sleepArray = output.getJSONArray("sleep");
            double epilepsyRisk = output.getDouble("epilepsy");

            // 4. 处理心脏病风险
            String heartDiseaseStatus;
            if (heartRisk >= 0.5) {
                heartDiseaseStatus = "差";
            } else {
                heartDiseaseStatus = "好";
            }

            // 5. 处理睡眠质量
            String sleepQualityStatus;
            int maxIndex = findMaxIndex(sleepArray);
            if (maxIndex == 0) {
                sleepQualityStatus = "差";
            } else if (maxIndex == 1) {
                sleepQualityStatus = "一般";
            } else {
                sleepQualityStatus = "好";
            }

            // 6. 处理癫痫风险
            String epilepsyStatus;
            if (epilepsyRisk >= 0.5) {
                epilepsyStatus = "有";
            } else {
                epilepsyStatus = "无";
            }

            // 创建最终的健康数据对象
            final String heartRate = String.format("%.2f", heartRateValue);
            final String bloodOxygen = String.format("%.2f", bloodOxygenValue);
            final String eeg1Str = String.format("%.2f", eeg1);
            final String eeg2Str = String.format("%.2f", eeg2);
            final String eeg3Str = String.format("%.2f", eeg3);
            final String eeg4Str = String.format("%.2f", eeg4);
            final String heartDisease = heartDiseaseStatus;
            final String sleepQuality = sleepQualityStatus;
            final String epilepsyRiskStr = epilepsyStatus;

            // 更新UI
            mHandler.post(() -> {
                tvHeartRate.setText(heartRate + " BPM");
                tvBloodOxygen.setText(bloodOxygen + " %");
                tvHeartDisease.setText(heartDisease);

                // 使用上标显示mV²
                setEegText(tvEEG1, eeg1Str);
                setEegText(tvEEG2, eeg2Str);
                setEegText(tvEEG3, eeg3Str);
                setEegText(tvEEG4, eeg4Str);

                tvSleepQuality.setText(sleepQuality);
                tvEpilepsyRisk.setText(epilepsyRiskStr);
            });

        } catch (JSONException e) {
            Log.e("UDP", "JSON解析错误: " + e.getMessage());

            // 显示解析错误
            mHandler.post(() -> {
                tvHeartRate.setText("解析错误");
                tvBloodOxygen.setText("解析错误");
                setEegText(tvEEG1, "错误");
                setEegText(tvEEG2, "错误");
                setEegText(tvEEG3, "错误");
                setEegText(tvEEG4, "错误");
            });
        } catch (Exception e) {
            Log.e("UDP", "数据解析错误: " + e.getMessage());

            // 显示解析错误
            mHandler.post(() -> {
                tvHeartRate.setText("格式错误");
                tvBloodOxygen.setText("格式错误");
                setEegText(tvEEG1, "错误");
                setEegText(tvEEG2, "错误");
                setEegText(tvEEG3, "错误");
                setEegText(tvEEG4, "错误");
            });
        }
    }

    // 在数组中查找最大值的索引
    private int findMaxIndex(JSONArray array) throws JSONException {
        int maxIndex = 0;
        double maxValue = array.getDouble(0);

        for (int i = 1; i < array.length(); i++) {
            double value = array.getDouble(i);
            if (value > maxValue) {
                maxValue = value;
                maxIndex = i;
            }
        }

        return maxIndex;
    }

    // 设置脑波文本（带mV²上标）
    private void setEegText(TextView textView, String value) {
        SpannableString spannable = new SpannableString(value + " μV²");
        int start = spannable.length() - 1; // ²的位置
        int end = spannable.length();

        // 应用上标样式
        spannable.setSpan(new SuperscriptSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new RelativeSizeSpan(0.7f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        // 添加垂直偏移
        spannable.setSpan(new BaselineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        textView.setText(spannable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 停止定时刷新
        mHandler.removeCallbacks(refreshTask);

        // 停止UDP服务器
        if (udpServer != null) {
            udpServer.stopServer();
        }
    }
}