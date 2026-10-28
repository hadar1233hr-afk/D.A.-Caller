package com.dacaller.app;

import android.app.Application;

/** Install app-window UI and the lifecycle guard for the optional call-only external island. */
public final class DaCallerApplication extends Application {
 @Override public void onCreate(){super.onCreate();CallIslandOverlay.install(this);CallIslandController.install(this);}
}
