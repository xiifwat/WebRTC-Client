package io.openvidu.openvidu_android;

import android.app.Application;
import android.content.res.Resources;

/**
 * Created by Murtuza Rahman on 2019-10-03.
 */
public class OpenViduApp extends Application {
    public static int screenWidth = 500;
    public static int screenHeight = 500;
    public static int cellSize = 150;
    @Override
    public void onCreate() {
        super.onCreate ();
        screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        screenHeight = Resources.getSystem().getDisplayMetrics().heightPixels;
        cellSize = screenWidth / 3 - 20;
    }
}
