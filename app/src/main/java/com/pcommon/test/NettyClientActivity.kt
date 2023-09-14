package com.pcommon.test

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.taike.lib_im.netty.client.NettyTcpClient
import com.taike.lib_im.netty.client.listener.NettyClientListener
import com.taike.lib_im.netty.client.status.ConnectState
import kotlinx.android.synthetic.main.activity_netty_client.*

class NettyClientActivity(val layoutId: Int = R.layout.activity_netty_client) :
    AppCompatActivity(layoutId) {
    private val TAG = "NettyClientActivity"
    private lateinit var client: NettyTcpClient
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        client =
            NettyTcpClient.Builder().setMaxReconnectTimes(Int.MAX_VALUE).setHeartBeatInterval(10)
                .setNeedSendPong(false).setListener(object : NettyClientListener<String> {
                    override fun onMessageResponseClient(msg: String?, index: String?) {

                    }

                    override fun onClientStatusConnectChanged(
                        state: ConnectState, index: String?
                    ) {

                        // if (state != ConnectState.STATUS_CONNECT_SUCCESS) client.connect()
                    }
                }).setHeartBeatData("test").setIndex("A1").build()
    }

    fun onStart(view: View) {
        client.connect(etHost.text.toString(), 9527)
    }

    fun onSend(view: View) {
        val text = etInput.text.toString()
        val ret = client.sendMsgToServer(text)
        Log.d(TAG, "onSend() called with: ret= $ret")
    }

    fun onStop(view: View) {
        client.disconnect()
    }
}