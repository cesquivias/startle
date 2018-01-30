package startle;

import android.app.Activity;
import android.os.Bundle;

import startle.annotation.Startle;
import startle.sample.R;

@Startle
public class BasicActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_basic);
    }
}
