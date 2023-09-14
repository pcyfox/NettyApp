package com.pcommon.test

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_test.*


class TestActivity(val layoutId: Int = R.layout.activity_test) :
    AppCompatActivity(layoutId) {
    private val TAG = "TestActivity"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initListener()
    }

    fun initListener() {
        btnNettyClient.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    NettyClientActivity::class.java
                )
            )
        }
        btnNettyServer.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    NettyServerActivity::class.java
                )
            )
        }
    }


}