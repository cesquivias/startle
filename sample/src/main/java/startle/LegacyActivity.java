package startle;

import android.app.Activity;
import android.graphics.Bitmap;
import android.support.annotation.Nullable;

import java.io.Serializable;

import startle.annotation.RequestExtra;
import startle.annotation.Startle;

@Startle
public class LegacyActivity extends Activity {
    @RequestExtra(long.class)
    public static final String EXTRA_ID = "id";
    @RequestExtra(Bitmap.class)
    public static final String EXTRA_SOME_OBJECT = "some-object";
    @RequestExtra(String.class)
    @Nullable
    public static final String EXTRA_MESSAGE = "message";
}
