package com.pcommon.application;

import android.app.Application;
import android.content.Context;

import androidx.multidex.MultiDex;

import com.elvishew.xlog.XLog;

public class App extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        XLog.init();
    }
}
