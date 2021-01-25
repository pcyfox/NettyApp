package com.pcommon.lib_network.udp;

import androidx.annotation.Keep;

import com.elvishew.xlog.XLog;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by melo on 2017/9/20.
 */
@Keep
public class UDPSocketClient {
    private OnSocketMsgArrivedListener msgArrivedListener;
    private OnStateChangeLister onStateChangeLister;
    private static UDPSocketClient instance;
    private static final String TAG = "UDPSocket";
    // 单个CPU线程池大小
    private static final int POOL_SIZE = 5;
    private static final int BUFFER_LENGTH = 8 * 1024;
    private final byte[] receiveByte = new byte[BUFFER_LENGTH];

    private static final String BROADCAST_IP = "255.255.255.255";

    // 端口号，
    private int CLIENT_PORT = 1999;

    private volatile boolean isThreadRunning = false;
    private DatagramSocket datagramSocket;
    private DatagramPacket receivePacket;
    private long lastReceiveTime = 0;
    private static final long TIME_OUT = 120 * 1000;
    private static final long HEARTBEAT_MESSAGE_DURATION = 10 * 1000;

    private final ExecutorService mThreadPool;
    private HeartbeatTimer timer;

    public boolean isStarted() {
        return isThreadRunning;
    }

    private UDPSocketClient() {
        int cpuNumbers = Runtime.getRuntime().availableProcessors();
        // 根据CPU数目初始化线程池
        mThreadPool = Executors.newFixedThreadPool(cpuNumbers * POOL_SIZE);
        // 记录创建对象时的时间
        lastReceiveTime = System.currentTimeMillis();
    }

    private UDPSocketClient(int port) {
        this();
        CLIENT_PORT = port;
    }

    public static UDPSocketClient getInstance() {
        if (instance == null) {
            instance = new UDPSocketClient();
        }
        return instance;
    }

    public void setClientPort(int clientPort) {
        CLIENT_PORT = clientPort;
    }

    public void startUDPSocket(int port) {
        setClientPort(port);
        startUDPSocket();
    }

    public void stopUDPSocket() {
        isThreadRunning = false;
        if (datagramSocket != null) {
            datagramSocket.disconnect();
            datagramSocket.close();
            XLog.d(TAG + ";stopUDPSocket() called socket closed");
            datagramSocket = null;
        }
        isThreadRunning = false;
        receivePacket = null;
        if (timer != null) {
            timer.exit();
        }

        if (onStateChangeLister != null) {
            onStateChangeLister.onStop();
        }
    }

    public void startUDPSocket() {
        XLog.d(TAG + ":startUDPSocket() called client=" + datagramSocket + " isThreadRunning=" + isThreadRunning);
        if (datagramSocket != null || isThreadRunning) return;
        try {
            datagramSocket = new DatagramSocket(null);
            datagramSocket.setReuseAddress(true);
            datagramSocket.bind(new InetSocketAddress(CLIENT_PORT));
            if (receivePacket == null) {
                // 创建接受数据的 packet
                receivePacket = new DatagramPacket(receiveByte, BUFFER_LENGTH);
            }

            startSocketThread();
        } catch (SocketException e) {
            XLog.e(TAG + ":startUDPSocket() error =" + e.getMessage());
            if (datagramSocket != null) {
                datagramSocket.disconnect();
                datagramSocket.close();
            }
            e.printStackTrace();
        }

    }

    public OnSocketMsgArrivedListener getMsgArrivedListener() {
        return msgArrivedListener;
    }

    public void setMsgArrivedListener(OnSocketMsgArrivedListener msgArrivedListener) {
        this.msgArrivedListener = msgArrivedListener;
    }

    /**
     * 开启发送数据的线程
     */
    private void startSocketThread() {
        isThreadRunning = true;
        Thread clientThread = new Thread(new Runnable() {
            @Override
            public void run() {
                XLog.i(TAG + ":clientThread start to working");
                receiveMessage();
                isThreadRunning = false;
                XLog.e(TAG + ":clientThread work broken!");
                if (onStateChangeLister != null) {
                    onStateChangeLister.onStop();
                }
            }
        });
        clientThread.start();
        if (onStateChangeLister != null) {
            onStateChangeLister.onStart();
        }
    }

    /**
     * 处理接受到的消息
     */
    private void receiveMessage() {
        while (isThreadRunning && !Thread.interrupted()) {
            try {
                if (receivePacket == null || datagramSocket == null || datagramSocket.isClosed() || !isThreadRunning || datagramSocket.isConnected()) {
                    return;
                }
                datagramSocket.receive(receivePacket);
                lastReceiveTime = System.currentTimeMillis();
            } catch (IOException e) {
                XLog.d("UDP数据包接收失败！线程停止");
                stopUDPSocket();
                e.printStackTrace();
                return;
            }
            if (receivePacket == null || receivePacket.getLength() == 0 || receivePacket.getAddress() == null) {
                XLog.d("无法接收UDP数据或者接收到的UDP数据为空");
                continue;
            }
            //String strReceive = new String(receivePacket.getData(), 0, receivePacket.getLength(),new C);
            try {
                String strReceive = new String(receivePacket.getData(), 0, receivePacket.getLength(), "utf-8");
                String host = (receivePacket.getAddress() == null) ? "null" : receivePacket.getAddress().getHostAddress();
                XLog.d("接收到广播数据 " + host + ":" + receivePacket.getPort() + "  内容  " + strReceive);
                if (msgArrivedListener != null) {
                    msgArrivedListener.onSocketMsgArrived(strReceive);
                }
                // 每次接收完UDP数据后，重置长度。否则可能会导致下次收到数据包被截断。
                if (receivePacket != null) {
                    receivePacket.setLength(BUFFER_LENGTH);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }


    public void clear() {
        stopUDPSocket();
        onStateChangeLister = null;
        msgArrivedListener = null;
    }


    /**
     * 启动心跳，timer 间隔十秒
     */
    private void startHeartbeatTimer() {
        if (timer != null) {
            timer.exit();
        }
        timer = new HeartbeatTimer();
        timer.setOnScheduleListener(new HeartbeatTimer.OnScheduleListener() {
            @Override
            public void onSchedule() {
                XLog.d("timer is onSchedule...");
                long duration = System.currentTimeMillis() - lastReceiveTime;
                XLog.d("duration:" + duration);
                if (duration > TIME_OUT) {//若超过两分钟都没收到我的心跳包，则认为对方不在线。
                    XLog.d("超时，对方已经下线");
                    // 刷新时间，重新进入下一个心跳周期
                    lastReceiveTime = System.currentTimeMillis();
                } else if (duration > HEARTBEAT_MESSAGE_DURATION) {//若超过十秒他没收到我的心跳包，则重新发一个。
                    String string = "hello,this is a heartbeat message";
                    sendBroadcast(string);
                }
            }

        });
        timer.startTimer(0, 1000 * 10);
    }


    public void sendBroadcast(final String message) {
        sendBroadcast(message, CLIENT_PORT);
    }

    public void sendBroadcast(final String message, final int port) {
        //说明通过指定端口创建的socket失败
        if (datagramSocket == null || datagramSocket.getPort() < 0) {
            try {
                //使用系统分配端口创建socket，保证消息能正常发送
                datagramSocket = new DatagramSocket();
            } catch (SocketException e) {
                XLog.e(TAG + ";sendBroadcast() called with: message = [" + message + "], port = [" + port + "] create socket error:" + e.getMessage());
                e.printStackTrace();
                return;
            }
        }
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    InetAddress targetAddress = InetAddress.getByName(BROADCAST_IP);
                    DatagramPacket packet = new DatagramPacket(message.getBytes(), message.getBytes().length, targetAddress, port);
                    try {
                        datagramSocket.send(packet);
                        XLog.d(TAG + ":sendBroadcast message:" + message);
                    } catch (IOException e) {
                        XLog.e(TAG + "sendBroadcast error:" + e.getMessage());
                        e.printStackTrace();
                    }
                } catch (UnknownHostException e) {
                    XLog.e(TAG + "sendBroadcast error:" + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }

    public void sendMessage(final String message, final String Ip, final int port) {
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    InetAddress targetAddress = InetAddress.getByName(Ip);
                    DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(), targetAddress, port);
                    XLog.d("client.send    " + datagramSocket);
                    datagramSocket.send(packet);
                    // 数据发送事件
                    XLog.d("数据发送成功");
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        });
    }

    public OnStateChangeLister getOnStateChangeLister() {
        return onStateChangeLister;
    }

    public void setOnStateChangeLister(OnStateChangeLister onStateChangeLister) {
        this.onStateChangeLister = onStateChangeLister;
    }

    public interface OnSocketMsgArrivedListener {
        void onSocketMsgArrived(String msg);
    }


    public interface OnStateChangeLister {
        void onStart();

        void onStop();
    }


}
