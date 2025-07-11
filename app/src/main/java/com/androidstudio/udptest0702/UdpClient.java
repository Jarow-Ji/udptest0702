package com.androidstudio.udptest0702;

import android.util.Log;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UdpClient {

    private static final String TAG = "UdpClient";

    /**
     * 向指定 IP 和端口发送数据
     * @param serverIp 目标服务器 IP
     * @param serverPort 目标服务器端口
     * @param message 要发送的数据
     * @return true 表示发送成功，false 表示发送失败
     */
    public static boolean sendUdpMessage(String serverIp, int serverPort, String message) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            InetAddress serverAddress = InetAddress.getByName(serverIp);
            byte[] data = message.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, serverPort);
            socket.send(packet);
            Log.d(TAG, "UDP 消息发送成功: " + message);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "UDP 消息发送失败: " + e.getMessage());
            return false;
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }
    public static void replyToSender(String message, InetAddress address, int port) {
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] data = message.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            socket.send(packet);
            Log.d(TAG, "回复发送成功: " + message);
        } catch (Exception e) {
            Log.e(TAG, "回复发送失败: " + e.getMessage());
        }
    }
}
