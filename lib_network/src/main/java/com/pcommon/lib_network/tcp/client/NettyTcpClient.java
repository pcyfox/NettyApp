package com.pcommon.lib_network.tcp.client;

import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import com.elvishew.xlog.XLog;
import com.pcommon.lib_network.tcp.client.handler.NettyClientCallback;
import com.pcommon.lib_network.tcp.client.handler.NettyClientHandler;
import com.pcommon.lib_network.tcp.client.listener.MessageStateListener;
import com.pcommon.lib_network.tcp.client.listener.NettyClientListener;
import com.pcommon.lib_network.tcp.client.status.ConnectState;

import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;

/**
 * Created by littleGreens on 2018-11-10.
 * TCP 客户端
 */
public class NettyTcpClient {
    private static final String TAG = "NettyTcpClient";

    private EventLoopGroup group;

    private NettyClientListener<String> listener;

    private Channel channel;

    private volatile boolean isConnect = false;

    /**
     * 最大重连次数
     */
    private int MAX_CONNECT_TIMES = Integer.MAX_VALUE;

    private int reconnectNum = MAX_CONNECT_TIMES;

    private boolean isNeedReconnect = true;


    private long reconnectIntervalTime = 5000;
    private static final Integer CONNECT_TIMEOUT_MILLIS = 5000;

    private String host;
    private int tcp_port;
    private String mIndex;
    /**
     * 心跳间隔时间
     */
    private long heartBeatInterval = 5;//单位秒

    /**
     * 是否发送心跳
     */
    private boolean isSendheartBeat = true;

    /**
     * 心跳数据，可以是String类型，也可以是byte[].
     */
    private Object heartBeatData;

    private String packetSeparator;
    private int maxPacketLong = 1024;

    private void setPacketSeparator(String separator) {
        this.packetSeparator = separator;
    }

    private void setMaxPacketLong(int maxPacketLong) {
        this.maxPacketLong = maxPacketLong;
    }

    private NettyTcpClient(String host, int tcp_port, String index) {
        this.host = host;
        this.tcp_port = tcp_port;
        this.mIndex = index;
    }

    public int getMaxConnectTimes() {
        return MAX_CONNECT_TIMES;
    }

    public long getReconnectIntervalTime() {
        return reconnectIntervalTime;
    }

    public String getHost() {
        return host;
    }

    public int getTcp_port() {
        return tcp_port;
    }

    public String getIndex() {
        return mIndex;
    }

    public long getHeartBeatInterval() {
        return heartBeatInterval;
    }

    public boolean isSendheartBeat() {
        return isSendheartBeat;
    }

    public void connect() {
        if (isConnect) {
            return;
        }
        Thread clientThread = new Thread("client-Netty") {
            @Override
            public void run() {
                super.run();
                isNeedReconnect = true;
                reconnectNum = MAX_CONNECT_TIMES;
                connectServer();
            }
        };
        clientThread.start();
    }


    private NettyClientCallback nettyClientCallback = new NettyClientCallback() {

        @Override
        public void onConnect() {
            isConnect = true;
        }

        @Override
        public void onDisconnect() {
            isConnect = false;
        }
    };

    private void connectServer() {
        synchronized (NettyTcpClient.this) {
            ChannelFuture channelFuture = null;
            if (!isConnect) {
                group = new NioEventLoopGroup();
                Bootstrap bootstrap = new Bootstrap().group(group)
                        .option(ChannelOption.TCP_NODELAY, true)//屏蔽Nagle算法试图
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            public void initChannel(SocketChannel ch) throws Exception {
                                if (isSendheartBeat) {
                                    ch.pipeline().addLast("ping", new IdleStateHandler(0, heartBeatInterval, 0, TimeUnit.SECONDS));//5s未发送数据，回调userEventTriggered
                                }

                                //黏包处理,需要客户端、服务端配合
                                if (!TextUtils.isEmpty(packetSeparator)) {
                                    ByteBuf delimiter = Unpooled.buffer();
                                    delimiter.writeBytes(packetSeparator.getBytes());
                                    ch.pipeline().addLast(new DelimiterBasedFrameDecoder(maxPacketLong, delimiter));
                                } else {
                                    ch.pipeline().addLast(new LineBasedFrameDecoder(maxPacketLong));
                                }

                                ch.pipeline().addLast(new StringEncoder(CharsetUtil.UTF_8));
                                ch.pipeline().addLast(new StringDecoder(CharsetUtil.UTF_8));
                                // 定义一个发送消息协议格式：|--header:4 byte--|--content:2MB--|
                                ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(1024 * 1024 * 2, 0, 4, 0, 4));
                                ch.pipeline().addLast(new NettyClientHandler(listener, mIndex, isSendheartBeat, heartBeatData, packetSeparator, nettyClientCallback));
                            }
                        });

                try {
                    channelFuture = bootstrap.connect(host, tcp_port).addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture channelFuture) throws Exception {
                            if (channelFuture.isSuccess()) {
                                isConnect = true;
                                XLog.i(TAG + ":连接成功");
                                reconnectNum = MAX_CONNECT_TIMES;
                                channel = channelFuture.channel();
                                listener.onClientStatusConnectChanged(ConnectState.STATUS_CONNECT_SUCCESS, mIndex);
                            } else {
                                XLog.w(TAG + ":连接失败");
                                listener.onClientStatusConnectChanged(ConnectState.STATUS_CONNECT_ERROR, mIndex);
                                isConnect = false;
                            }
                        }
                    }).sync();

                    // Wait until the connection is closed.
                    channelFuture.channel().closeFuture().sync();
                    Log.e(TAG, " 断开连接");
                } catch (Exception e) {
                    e.printStackTrace();
                    isConnect = false;
                    XLog.d(TAG + "netty客户端----> connectServer   Exception 连接失败");
                    listener.onClientStatusConnectChanged(ConnectState.STATUS_CONNECT_CLOSED, mIndex);
                    if (null != channelFuture) {
                        if (channelFuture.channel() != null && channelFuture.channel().isOpen()) {
                            channelFuture.channel().close();
                        }
                    }
                    group.shutdownGracefully();
                    try {
                        XLog.d(TAG + "netty客户端----> connectServer    Thread.sleep ");
                        Thread.sleep(5000);
                    } catch (Exception ex) {
                        //不会调用
                    }
                    XLog.d(TAG + "netty客户端----> connectServer    Thread.sleep +");
                    reconnect();
                } finally {

                }
            }
        }
    }


    public void disconnect() {
        XLog.w(TAG, "disconnect() called!");
        isNeedReconnect = false;
        isConnect = false;
        group.shutdownGracefully();
    }

    public void reconnect() {
        if (isNeedReconnect && reconnectNum > 0 && !isConnect) {
            reconnectNum--;
            SystemClock.sleep(reconnectIntervalTime);
            if (isNeedReconnect && reconnectNum > 0 && !isConnect) {
                XLog.w(TAG + ":重新连接,第%d次", reconnectNum);
                connectServer();
            }
        }
    }

    /**
     * 异步发送
     *
     * @param data     要发送的数据
     * @param listener 发送结果回调
     * @return 方法执行结果
     */
    public boolean sendMsgToServer(String data, final MessageStateListener listener) {
        boolean flag = channel != null && isConnect;
        if (flag) {
            String separator = TextUtils.isEmpty(packetSeparator) ? System.getProperty("line.separator") : packetSeparator;
            channel.pipeline().addLast(new LengthFieldPrepender(4));
            ChannelFuture channelFuture = channel.writeAndFlush(data + separator).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture channelFuture) throws Exception {
                    listener.isSendSuccss(channelFuture.isSuccess());
                }
            });
        }
        return flag;
    }

    /**
     * 同步发送
     *
     * @param data 要发送的数据
     * @return 方法执行结果
     */
    public boolean sendMsgToServer(String data) {
        boolean flag = channel != null && isConnect;
        if (flag) {
            String separator = TextUtils.isEmpty(packetSeparator) ? System.getProperty("line.separator") : packetSeparator;
            ChannelFuture channelFuture = channel.writeAndFlush(data + separator).awaitUninterruptibly();
            return channelFuture.isSuccess();
        }
        return false;
    }


    public boolean sendMsgToServer(byte[] data, final MessageStateListener listener) {
        boolean flag = channel != null && isConnect;
        if (flag) {
            ByteBuf buf = Unpooled.copiedBuffer(data);
            channel.writeAndFlush(buf).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture channelFuture) throws Exception {
                    listener.isSendSuccss(channelFuture.isSuccess());
                }
            });
        }
        return flag;
    }

    /**
     * 获取TCP连接状态
     *
     * @return 获取TCP连接状态
     */
    public boolean getConnectStatus() {
        return isConnect;
    }

    public boolean isConnecting() {
        return isConnect;
    }

    public void setConnectStatus(boolean status) {
        this.isConnect = status;
    }

    public void setListener(NettyClientListener listener) {
        this.listener = listener;
    }

    public byte[] strToByteArray(String str) {
        if (str == null) {
            return null;
        }
        byte[] byteArray = str.getBytes();
        return byteArray;

    }

    /**
     * 构建者，创建NettyTcpClient
     */
    public static class Builder {

        /**
         * 最大重连次数
         */
        private int MAX_CONNECT_TIMES = Integer.MAX_VALUE;

        /**
         * 重连间隔
         */
        private long reconnectIntervalTime = 5000;
        /**
         * 服务器地址
         */
        private String host;
        /**
         * 服务器端口
         */
        private int tcp_port;
        /**
         * 客户端标识，(因为可能存在多个连接)
         */
        private String mIndex;

        /**
         * 是否发送心跳
         */
        private boolean isSendheartBeat;
        /**
         * 心跳时间间隔
         */
        private long heartBeatInterval = 5;

        /**
         * 心跳数据，可以是String类型，也可以是byte[].
         */
        private Object heartBeatData;

        private String packetSeparator;
        private int maxPacketLong = 1024;

        public Builder() {
            this.maxPacketLong = 1024;
        }


        public Builder setPacketSeparator(String packetSeparator) {
            this.packetSeparator = packetSeparator;
            return this;
        }

        public Builder setMaxPacketLong(int maxPacketLong) {
            this.maxPacketLong = maxPacketLong;
            return this;
        }

        public Builder setMaxReconnectTimes(int reConnectTimes) {
            this.MAX_CONNECT_TIMES = reConnectTimes;
            return this;
        }


        public Builder setReconnectIntervalTime(long reconnectIntervalTime) {
            this.reconnectIntervalTime = reconnectIntervalTime;
            return this;
        }


        public Builder setHost(String host) {
            this.host = host;
            return this;
        }

        public Builder setTcpPort(int tcp_port) {
            this.tcp_port = tcp_port;
            return this;
        }

        public Builder setIndex(String mIndex) {
            this.mIndex = mIndex;
            return this;
        }

        public Builder setHeartBeatInterval(long intervalTime) {
            this.heartBeatInterval = intervalTime;
            return this;
        }

        public Builder setSendheartBeat(boolean isSendheartBeat) {
            this.isSendheartBeat = isSendheartBeat;
            return this;
        }

        public Builder setHeartBeatData(Object heartBeatData) {
            this.heartBeatData = heartBeatData;
            return this;
        }

        public NettyTcpClient build() {
            NettyTcpClient nettyTcpClient = new NettyTcpClient(host, tcp_port, mIndex);
            nettyTcpClient.MAX_CONNECT_TIMES = this.MAX_CONNECT_TIMES;
            nettyTcpClient.reconnectIntervalTime = this.reconnectIntervalTime;
            nettyTcpClient.heartBeatInterval = this.heartBeatInterval;
            nettyTcpClient.isSendheartBeat = this.isSendheartBeat;
            nettyTcpClient.heartBeatData = this.heartBeatData;
            nettyTcpClient.packetSeparator = this.packetSeparator;
            nettyTcpClient.maxPacketLong = this.maxPacketLong;
            return nettyTcpClient;
        }
    }

    public Channel getChannel() {
        return channel;
    }
}
