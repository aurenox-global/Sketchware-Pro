package pro.sketchware;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.lifecycle.Lifecycle;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.junit.Test;
import org.junit.runner.RunWith;

import pro.sketchware.R;
import pro.sketchware.activities.main.activities.MainActivity;

@RunWith(AndroidJUnit4.class)
public class AndroidDeviceSmokeTest {

    @Test
    public void appContext_hasExpectedPackageName() {
        Context appContext = ApplicationProvider.getApplicationContext();
        assertEquals("pro.sketchware", appContext.getPackageName());
    }

    @Test
    public void launcherActivity_isResolvable() {
        Context appContext = ApplicationProvider.getApplicationContext();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        launcherIntent.setPackage(appContext.getPackageName());

        // En un emulador recien arrancado el PackageManager puede tardar un instante en indexar
        // la app recien instalada, y el primer resolveActivity devuelve null. Reintentamos unos
        // segundos en vez de dar el test por fallido.
        ResolveInfo resolveInfo = null;
        for (int attempt = 0; attempt < 10 && resolveInfo == null; attempt++) {
            resolveInfo = appContext
                    .getPackageManager()
                    .resolveActivity(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolveInfo == null) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        assertNotNull("Launcher activity should be resolvable", resolveInfo);
    }

    @Test
    public void mainActivity_launchesSuccessfully() {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        try {
            scenario.onActivity(activity -> assertNotNull("MainActivity should be created", activity));
        } finally {
            scenario.close();
        }
    }

    @Test
    public void mainActivity_recreate_keepsCoreViewsAvailable() {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        try {
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertFalse("MainActivity should not be finishing", activity.isFinishing());
                assertNotNull("DrawerLayout must exist", activity.findViewById(R.id.drawer_layout));
                assertNotNull("Bottom nav must exist", activity.findViewById(R.id.bottom_nav));
            });
        } finally {
            scenario.close();
        }
    }

    @Test
    public void mainActivity_bottomNav_hasStableItemsAfterLifecycleBounce() {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        try {
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.moveToState(Lifecycle.State.STARTED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> {
                BottomNavigationView bottomNav = activity.findViewById(R.id.bottom_nav);
                assertNotNull("Bottom nav must exist", bottomNav);
                assertTrue("Bottom nav should expose at least two tabs", bottomNav.getMenu().size() >= 2);
                assertNotNull(
                        "Projects tab should be present",
                        bottomNav.getMenu().findItem(R.id.item_projects)
                );
                assertNotNull(
                        "Store tab should be present",
                        bottomNav.getMenu().findItem(R.id.item_sketchub)
                );
            });
        } finally {
            scenario.close();
        }
    }
}
