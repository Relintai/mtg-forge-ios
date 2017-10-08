package forge.app;

import com.badlogic.gdx.backends.iosmoe.IOSApplication;
import com.badlogic.gdx.backends.iosmoe.IOSApplicationConfiguration;
import org.moe.natj.general.Pointer;

import com.badlogic.gdx.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.backends.iosmoe.IOSFiles;
import com.badlogic.gdx.utils.Clipboard;

import forge.Forge;
import forge.interfaces.IDeviceAdapter;

import apple.uikit.c.UIKit;

public class IOSMoeLauncher extends IOSApplication.Delegate {

    protected IOSMoeLauncher(Pointer peer) {
        super(peer);
    }

    @Override
    protected IOSApplication createApplication() {
        IOSApplicationConfiguration config = new IOSApplicationConfiguration();
        config.useAccelerometer = false;

        IOSFiles iosf = new IOSFiles();

        return new IOSApplication(Forge.getApp(new IOSClipboard(), new IOSMoeAdapter(), iosf.getExternalStoragePath() + "/Forge/"), config);
    }

    public static void main(String[] argv) {
        UIKit.UIApplicationMain(0, null, null, IOSMoeLauncher.class.getName());
    }

    private static class IOSClipboard implements Clipboard
    {

        @Override
        public String getContents() {
            return "";
        }

        @Override
        public void setContents(String s) {

        }
    }


    private static class IOSMoeAdapter implements IDeviceAdapter {

        private IOSMoeAdapter() {
        }

        @Override
        public boolean isConnectedToInternet() {
            return true;

            //return Boolean.TRUE.equals(ThreadUtil.executeWithTimeout(new Callable<Boolean>() {
          //      @Override
        //        public Boolean call() throws Exception {
     //               NetworkInfo activeNetworkInfo = connManager.getActiveNetworkInfo();
         //           return activeNetworkInfo != null && activeNetworkInfo.isConnected();
      //          }
       //     }, 2000)); //if can't determine Internet connection within two seconds, assume not connected
        }

        @Override
        public boolean isConnectedToWifi() {
            return true;

     //       return Boolean.TRUE.equals(ThreadUtil.executeWithTimeout(new Callable<Boolean>() {
       //         @Override
      //          public Boolean call() throws Exception {
      //              NetworkInfo wifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
      //              return wifi.isConnected();
      //          }
    //        }, 2000)); //if can't determine Internet connection within two seconds, assume not connected
        }

        @Override
        public String getDownloadsDir() {
            return Gdx.files.getLocalStoragePath() + "/downloads";

     //       return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/";
        }

        @Override
        public boolean openFile(String filename) {

            FileHandle fh = Gdx.files.absolute(filename);

            return fh.exists();

            /*

            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); //ensure this task isn't linked to this application
                Uri uri = Uri.fromFile(new File(filename));
                String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                        MimeTypeMap.getFileExtensionFromUrl(uri.toString()));
                intent.setDataAndType(uri, type);
                startActivity(intent);
                return true;
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            return false;

            */
        }

        @Override
        public void restart() {

            /*

            try { //solution from http://stackoverflow.com/questions/6609414/howto-programatically-restart-android-app
                Context c = getApplicationContext();
                PackageManager pm = c.getPackageManager();
                if (pm != null) {
                    //create the intent with the default start activity for your application
                    Intent mStartActivity = pm.getLaunchIntentForPackage(c.getPackageName());
                    if (mStartActivity != null) {
                        mStartActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        //create a pending intent so the application is restarted after System.exit(0) was called.
                        // We use an AlarmManager to call this intent in 100ms
                        int mPendingIntentId = 223344;
                        PendingIntent mPendingIntent = PendingIntent.getActivity(c, mPendingIntentId, mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT);
                        AlarmManager mgr = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
                        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
                        //kill the application
                        System.exit(0);
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            */
        }

        @Override
        public void exit() {

            /*
            // Replace the current task with one that is excluded from the recent
            // apps and that will finish itself immediately. It's critical that this
            // activity get launched in the task that you want to hide.
            final Intent relaunch = new Intent(getApplicationContext(), Exiter.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK // CLEAR_TASK requires this
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK // finish everything else in the task
                            | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS); // hide (remove, in this case) task from recents
            startActivity(relaunch);

            */
        }

        @Override
        public boolean isTablet() {
            return false;
        }

        @Override
        public void setLandscapeMode(boolean landscapeMode) {

            /*
            //create file to indicate that portrait mode should be used for tablet or landscape should be used for phone
            if (landscapeMode != isTablet) {
                FileUtil.writeFile(switchOrientationFile, "1");
            }
            else {
                FileUtil.deleteFile(switchOrientationFile);
            }

            */
        }

        @Override
        public void preventSystemSleep(final boolean preventSleep) {

            /*
            FThreads.invokeInEdtNowOrLater(new Runnable() { //must set window flags from EDT thread
                @Override
                public void run() {
                    if (preventSleep) {
                        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    }
                    else {
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    }
                }
            });

            */
        }
    }
}
