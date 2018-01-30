package startle;

import android.app.Activity;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;

import java.util.HashMap;

import startle.annotation.RequestExtra;
import startle.annotation.Startle;

@Startle
public class SampleActivity extends Activity {
    @RequestExtra long id;
    @RequestExtra HashMap<String, String> data;
    @RequestExtra @Nullable String message;
    @RequestExtra @Flag int flag;

    @IntDef({FLAG_FOO, FLAG_BAR})
    @interface Flag{}

    public static final int FLAG_FOO = 1;
    public static final int FLAG_BAR = 2;
}
