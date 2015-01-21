package org.akvo.appium;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.io.File;
import java.net.URL;

/**
 * Author: Ruarcc McAloon
 * Date: 20/01/15
 * This test class is identical to FLOWTests.java except the tests are run on SauceLabs
 */
public class FLOWTestSauceLabs {

    private AppiumDriver driver;

    private static final String FLOW_APP = "flow-2.1.0.1.apk";

    @Before
    public void setUp() throws Exception {
        File classpathRoot = new File(System.getProperty("user.dir"));
        File appDir = new File(classpathRoot, "../apps/");
        File app = new File(appDir, FLOW_APP);

        DesiredCapabilities capabilities = new DesiredCapabilities();
        capabilities.setCapability(CapabilityType.BROWSER_NAME, "");
        capabilities.setCapability("deviceName","6435b877");
        capabilities.setCapability("platformVersion", "4.4.2");
        capabilities.setCapability("platformName","Android");
        capabilities.setCapability("app", app.getAbsolutePath());

        driver = new AndroidDriver(new URL("http://127.0.0.1:4723/wd/hub"), capabilities);
    }

    @Test
    public void testSurveySubmissionWithMandatoryFields(){

    }

    @After
    public void tearDown() throws Exception {
        driver.quit();
    }
}
