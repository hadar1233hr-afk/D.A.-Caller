package com.dacaller.app;

import android.app.Application;

/** App-window UI only: no system overlay or additional permission. */
public final class DaCallerApplication extends Application {
 @Override public void onCreate(){super.onCreate();CallIslandController.install(this);}
}
