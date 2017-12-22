package tapsi.geodoor;

import android.app.Application;
import android.content.Context;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

@ReportsCrashes(
        formUri = "http://collector.tracepot.com/3da5bf94",
        mode = ReportingInteractionMode.TOAST,
        resToastText = R.string.crash_toast_text)

public class ACRAApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        // The following line triggers the initialization of ACRA
        ACRA.init(this);
    }
}
