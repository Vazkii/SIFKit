package vazkii.sifkit;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import android.util.Log;

public class SIFKit extends Activity {

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        Log.i("SIFKit", "SIFKit Created");

        if(SIFKitService.connected)
            Toast.makeText(this, "SIFKit is already running", Toast.LENGTH_SHORT).show();
        else {
            Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivityForResult(intent, 0);

            Toast.makeText(this, "Please find SIFKit and enable Accessibility", Toast.LENGTH_LONG).show();
        }

        finish();
    }

}
