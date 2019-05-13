package ja.burhanrashid52.photoeditor;

import android.util.Log;

public class DefaultLogger implements Logger {

    @Override
    public void log(String message, Throwable t) {
        Log.e("PhotoEditor", message, t);
    }

}
