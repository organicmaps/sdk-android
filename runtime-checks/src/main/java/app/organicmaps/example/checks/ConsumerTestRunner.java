package app.organicmaps.example.checks;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

/**
 * Platform-only probe for the minified release APK. Sharing a third-party test runner's dependencies with a
 * separately optimized target can require extra keep rules, which would weaken this consumer check.
 * Resolve SDK/HTTP classes through the target loader so test-APK dependencies cannot satisfy a missing target type.
 */
public final class ConsumerTestRunner extends Instrumentation
{
  @Override
  public void onCreate(Bundle arguments)
  {
    super.onCreate(arguments);
    start();
  }

  @Override
  public void onStart()
  {
    Bundle status = new Bundle();
    status.putString("id", "InstrumentationTestRunner");
    status.putString("class", getClass().getName());
    status.putString("test", "initializesAndRendersFromPublishedAar");
    status.putInt("numtests", 1);
    status.putInt("current", 1);
    sendStatus(1, status);
    Activity activity = null;
    Throwable failure = null;
    try
    {
      // JNI uses this literal external return type and method name; target-app R8 must preserve both.
      ClassLoader loader = getTargetContext().getClassLoader();
      Class<?> http = loader.loadClass("app.organicmaps.sdk.util.HttpClient");
      Class<?> params = loader.loadClass("app.organicmaps.sdk.util.HttpClient$Params");
      Class<?> call = http.getDeclaredMethod("runAsync", params, long.class).getReturnType();
      if (!"okhttp3.Call".equals(call.getName()))
        throw new AssertionError("JNI HTTP return type was renamed to " + call.getName());
      if (call.getMethod("cancel").getReturnType() != void.class)
        throw new AssertionError("JNI HTTP cancellation must have descriptor ()V");

      Intent intent =
          getTargetContext().getPackageManager().getLaunchIntentForPackage(getTargetContext().getPackageName());
      if (intent == null)
        throw new AssertionError("The SDK consumer has no launch activity");
      if (!Activity.class.isAssignableFrom(loader.loadClass(intent.getComponent().getClassName())))
        throw new AssertionError("The consumer launcher is not an Activity");
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      activity = startActivitySync(intent);
      Activity target = activity;
      int mapId = getTargetContext().getResources().getIdentifier("map", "id", getTargetContext().getPackageName());
      int lanesId = getTargetContext().getResources().getIdentifier("consumer_lanes", "id", getTargetContext().getPackageName());
      int speedLimitId = getTargetContext().getResources().getIdentifier("consumer_speed_limit", "id", getTargetContext().getPackageName());
      long deadline = SystemClock.uptimeMillis() + 60000;
      boolean[] ready = {false};
      while (!ready[0] && SystemClock.uptimeMillis() < deadline)
      {
        runOnMainSync(() -> {
          View map = target.findViewById(mapId);
          View lanes = target.findViewById(lanesId);
          View speedLimit = target.findViewById(speedLimitId);
          ready[0] = map != null && Boolean.TRUE.equals(map.getTag()) && lanes != null && lanes.getWidth() > 0
                     && speedLimit != null && speedLimit.getWidth() > 0;
        });
        if (!ready[0])
          SystemClock.sleep(100);
      }
      if (!ready[0])
        throw new AssertionError("The published SDK did not initialize its renderer");

      // Region fields come from JNI; widgets are referenced and constructed by the target host itself.
      Throwable[] sdkError = {null};
      runOnMainSync(() -> {
        try
        {
          Class<?> manager = loader.loadClass("app.organicmaps.sdk.downloader.MapManager");
          Object root = manager.getMethod("nativeGetRoot").invoke(null);
          Class<?> country = loader.loadClass("app.organicmaps.sdk.downloader.CountryItem");
          Object region = country.getMethod("fill", String.class).invoke(null, root);
          Object name = country.getField("name").get(region);
          if (!(name instanceof String) || ((String) name).isEmpty())
            throw new AssertionError("JNI did not populate the region name");
        }
        catch (Throwable error)
        {
          sdkError[0] = error;
        }
      });
      if (sdkError[0] != null)
        throw new AssertionError("Published SDK region data or widgets are unusable", sdkError[0]);
    }
    catch (Throwable error)
    {
      failure = error;
    }
    finally
    {
      if (activity != null)
        runOnMainSync(activity::finish);
    }
    if (failure == null)
    {
      sendStatus(0, status);
      status.putString("stream", "\nOK (1 test)\n");
      finish(Activity.RESULT_OK, status);
    }
    else
    {
      status.putString("stack", Log.getStackTraceString(failure));
      sendStatus(-2, status);
      finish(Activity.RESULT_CANCELED, status);
    }
  }
}
