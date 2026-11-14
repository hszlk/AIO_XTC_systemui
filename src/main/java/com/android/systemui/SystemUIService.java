package com.android.systemui;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Host service of the SystemUI process (declared in AndroidManifest.xml).
 *
 * Original APK logic: onCreate() asks the application to start the component
 * list; in debuggable builds with debug.crash_sysui set it throws on purpose
 * (a developer switch); dump() forwards to every started SystemUI component,
 * optionally filtered by class-name suffix.
 *
 * Reimplementation logic: identical service contract, without the debug-crash
 * hidden-API switch (developer-only; not worth a reflection bridge in a release
 * build of the reimplementation).
 */
public class SystemUIService extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ((SystemUIApplication) getApplication()).startServicesIfNeeded();
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        SystemUI[] services = ((SystemUIApplication) getApplication()).getServices();
        if (services == null) {
            pw.println("SystemUI services not started");
            return;
        }
        if (args == null || args.length == 0) {
            for (SystemUI service : services) {
                pw.println("dumping service: " + service.getClass().getName());
                service.dump(fd, pw, args);
            }
            return;
        }
        String filter = args[0];
        for (SystemUI service : services) {
            if (service.getClass().getName().endsWith(filter)) {
                service.dump(fd, pw, args);
            }
        }
    }
}
