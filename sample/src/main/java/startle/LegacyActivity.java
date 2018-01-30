package startle;

import android.app.Activity;

import java.io.Serializable;

import startle.annotation.RequestExtra;
import startle.annotation.Startle;

@Startle
public class LegacyActivity extends Activity {
    @RequestExtra(long.class)
    public static final String EXTRA_ID = "id";
    @RequestExtra(Serializable.class)
    public static final String EXTRA_SOME_OBJECT = "some-object";
    @RequestExtra(value = String.class, optional = true)
    public static final String EXTRA_MESSAGE = "message";
}
