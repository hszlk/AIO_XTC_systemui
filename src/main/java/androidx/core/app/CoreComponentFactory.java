package androidx.core.app;

import android.app.Activity;
import android.app.AppComponentFactory;
import android.app.Application;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.Intent;

/**
 * Compatibility implementation of the {@code appComponentFactory} named by the
 * manifest ({@code androidx.core.app.CoreComponentFactory}).
 *
 * The original APK declared this androidx class as its component factory.  The
 * reimplemented APK is framework-only (no androidx jars), therefore this class
 * is implemented locally with the same contract: default instantiation
 * delegated to the framework while keeping the manifest attribute valid.
 */
public class CoreComponentFactory extends AppComponentFactory {

    @Override
    public Application instantiateApplication(ClassLoader cl, String className)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return super.instantiateApplication(cl, className);
    }

    @Override
    public Activity instantiateActivity(ClassLoader cl, String className, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return super.instantiateActivity(cl, className, intent);
    }

    @Override
    public Service instantiateService(ClassLoader cl, String className, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return super.instantiateService(cl, className, intent);
    }

    @Override
    public BroadcastReceiver instantiateReceiver(ClassLoader cl, String className, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return super.instantiateReceiver(cl, className, intent);
    }

    @Override
    public ContentProvider instantiateProvider(ClassLoader cl, String className)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return super.instantiateProvider(cl, className);
    }
}
