package com.androidstudio.udptest0702;

import android.util.Log;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.InetAddress; // 添加这行导入

public class UdpServer implements Runnable {
    private static final String TAG = "UdpServer";
    private final int port;
    private boolean isRunning = false;
    private DatagramSocket socket;
    private OnUdpReceiveListener listener;

    public UdpServer(int port) {
        this.port = port;
    }

    public void setOnUdpReceiveListener(OnUdpReceiveListener listener) {
        this.listener = listener;
    }

    public void startServer() {
        isRunning = true;
        new Thread(this).start();
    }

    public void stopServer() {
        isRunning = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    @Override
    public void run() {
        try {
            socket = new DatagramSocket(port);
            socket.setSoTimeout(0);
            Log.d(TAG, "UDP 服务端启动，端口：" + port);
            byte[] buffer = new byte[1024];
            while (isRunning) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                // 获取发送方的IP和端口
                InetAddress clientAddress = packet.getAddress();
                int clientPort = packet.getPort();
                String data = new String(packet.getData(), 0, packet.getLength());
                Log.d(TAG, "接收到数据：" + data);
                if (listener != null) {
                    // 将发送方信息传递给监听器
                    listener.onUdpReceive(data, clientAddress, clientPort);
                }
            }
        } catch (SocketException e) {
            if (isRunning) Log.e(TAG, "Socket异常: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "异常: " + e.getMessage());
        } finally {
            if (socket != null) socket.close();
            Log.d(TAG, "服务端停止");
        }
    }

    public interface OnUdpReceiveListener {
        void onUdpReceive(String data, InetAddress fromAddress, int fromPort);
    }
}
