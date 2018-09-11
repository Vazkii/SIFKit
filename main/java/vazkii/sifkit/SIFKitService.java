package vazkii.sifkit;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import java.util.Map;
import java.util.HashMap;

// Taken from https://stackoverflow.com/questions/3873659/android-how-can-i-get-the-current-foreground-activity-from-a-service/27642535#27642535
public class SIFKitService extends AccessibilityService {

    private static final String MESSENGER_ID = "com.facebook.orca";
    private static final String SIF_ID = "klb.android.lovelive";

    private static final Map<String, Integer> pids = new HashMap();

    public static boolean connected = false;
    private static DataOutputStream su = null;
    private static BufferedReader suReader;

    private static boolean notificationsEnabled = true;
    private static boolean facebookMessenger = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        Log.i("SIFKit", "Accessibility Service Enabled");

        AccessibilityServiceInfo config = new AccessibilityServiceInfo();
        config.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        config.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;

        if (Build.VERSION.SDK_INT >= 16)
            config.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;

        setServiceInfo(config);

        boolean root = getRoot();
        if(!root)
            Toast.makeText(this, "SIFKit can not run without Root.", Toast.LENGTH_LONG).show();
        else {
            Toast.makeText(this, "SIFKit is now running!", Toast.LENGTH_LONG).show();
            connected = true;
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if(event.getPackageName() == null)
                return;

            String pkg = event.getPackageName().toString();
            ComponentName componentName = new ComponentName(pkg, event.getClassName().toString());
            try {
                ActivityInfo activityInfo = getPackageManager().getActivityInfo(componentName, 0);

                if(activityInfo != null) {
                    boolean enable = !pkg.startsWith(SIF_ID);

                    if(!enable) {
                        int pid = getCurrentAppPID(pkg);
                        Log.i("SIFKit", pkg + " PID recognised as " + pid);
                        pids.put(pkg, pid);
                    } else {
                        lockApps();
                    }

                    Log.i("SIFKit", "Opened " + pkg);
                    if(pkg.equals(MESSENGER_ID)) {
                        facebookMessenger = true;
                        Log.i("SIFKit", "Facebook messenger has been loaded!");
                    }

                    if(notificationsEnabled != enable) {
                        setNotifications(enable);
                        notificationsEnabled = enable;
                    }
                }
            } catch(PackageManager.NameNotFoundException e) { }
        }
    }

    private boolean getRoot() {
        if(su != null)
            return true;

        boolean hasRoot = false;
        try {
            Process suProcess = Runtime.getRuntime().exec("su -c /system/bin/sh");
            DataOutputStream out = new DataOutputStream(suProcess.getOutputStream());
            out.writeBytes("exit\n");
            out.flush();
            suProcess.waitFor();
            if(suProcess.exitValue() == 0)  {
                hasRoot = true;
                suProcess = Runtime.getRuntime().exec("su");
                su = new DataOutputStream(suProcess.getOutputStream());
                suReader = new BufferedReader(new InputStreamReader(suProcess.getInputStream()));
            }
        } catch(IOException | InterruptedException e) { }

        return hasRoot;
    }

    private void setNotifications(boolean setting) {
        String command = "settings put global heads_up_notifications_enabled " + (setting ? 1 : 0);
        run(command);

        if(facebookMessenger) {
            String option = setting ? "grant" : "revoke";
            command = String.format("pm %s %s android.permission.SYSTEM_ALERT_WINDOW", option, MESSENGER_ID);
            run(command);
        }

        flush();
    }

    private int getCurrentAppPID(String target) {
        String cmd = "pidof " + target;
        String res = runSingle(cmd);
        Log.i("SIFKit", "PID: " + res);

        return Integer.valueOf(res);
    }

    private void lockApps() {
        if(pids.isEmpty())
            return;

        for(Integer i : pids.values())
            run("echo -17 > /proc/" + i + "/oom_adj");
        flush();
    }

    private String runSingle(String command) {
        run(command);
        return flush(true);
    }

    private void run(String command) {
        try {
            boolean executed = false;

            if(getRoot()) {
                Log.i("SIFKit", "Running SU Command: " + command);
                su.writeBytes(command);
                su.writeBytes("\n");
                executed = true;
            }

            Log.i("SIFKit", "Result: " + executed);
            if(!executed)
                su = null;
        } catch(IOException e) {
            Log.e("SIFKit", "Problem while running root command", e);
        }
    }

    private void flush() {
        flush(false);
    }

    private String flush(boolean getOut) {
        try {
            Log.i("SIFKit", "Flushing su buffers");
            su.flush();

            if(getOut) {
                String s;
                String result = "";
                if((s = suReader.readLine()) != null)
                    result = s;

                Log.i("SIFKit", "Result: " + result);
                return result;
            }

            return "";
        } catch(IOException e) {
            Log.e("SIFKit", "Problem while running root command", e);
            return "";
        }
    }

    @Override
    public void onInterrupt() {
        // NO-OP
    }

}
