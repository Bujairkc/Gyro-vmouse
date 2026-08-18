package com.bulgekeyboard;

import android.content.Context;
import java.io.*;

public class NativeRunner {

    private static void runRoot(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());

            os.writeBytes(cmd + "\n");
            os.writeBytes("exit\n");
            os.flush();
            os.close();

            p.waitFor();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void startVmouse(Context ctx) {
        try {
            File out = new File("/data/local/tmp/vmouse");

            InputStream is = ctx.getAssets().open("vmouse");
            FileOutputStream fos = new FileOutputStream(out);

            byte[] buf = new byte[4096];
            int len;

            while ((len = is.read(buf)) > 0) {
                fos.write(buf, 0, len);
            }

            fos.close();
            is.close();

            runRoot("chmod 755 /data/local/tmp/vmouse");
            runRoot("pgrep vmouse || /data/local/tmp/vmouse &");

            // 🔥 REAL FIX FOR HUAWEI (disable navbar)
            runRoot("settings put secure navigationbar_is_min 1");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void stopVmouse() {

        runRoot("pkill vmouse");

        // 🔥 restore navbar
        runRoot("settings put secure navigationbar_is_min 0");
    }
}