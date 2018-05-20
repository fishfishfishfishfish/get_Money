package com.example.a61915.server;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.example.a61915.utils.IsServerRunning;

public class LookServer extends Service {
    private String className = "GetMemoryServer";

    public LookServer() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        new Thread(new Runnable() {
            @Override
            public void run() {
                if(IsServerRunning.isStartAccessibilityService(LookServer.this,className)){
                    startService(new Intent(LookServer.this, GetMemoryServer.class));
                }
            }
        }).start();

        return super.onStartCommand(intent, flags, startId);
    }
}
