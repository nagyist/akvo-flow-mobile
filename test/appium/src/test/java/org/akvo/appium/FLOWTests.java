package org.akvo.appium;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.io.File;
import java.net.URL;
import java.util.concurrent.TimeUnit;

/**
 * Author: Ruarcc McAloon
 * Date: 20/01/15
 */
public class FLOWTests {

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
    public void testEmptyFormSubmission(){
        //add implicit wait to allow for surveys etc to load
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);

        //Confirm no users present
        driver.findElement(By.id("org.akvo.flow:id/users")).click();
        WebElement userList = driver.findElement(By.id("android:id/empty"));
        assertEquals("No Users!", userList.getText());

        //Add a new user and select
        driver.findElement(By.id("org.akvo.flow:id/add_user")).click();
        driver.findElement(By.id("org.akvo.flow:id/displayNameField")) .sendKeys("LearningIsFun");
        driver.findElement(By.id("org.akvo.flow:id/confirm")).click();

        driver.findElement(By.xpath("//android.widget.TextView[contains(@text,'LearningIsFun')]")).click();

        //Fetch test form
        driver.findElement(By.id("org.akvo.flow:id/settings")).click();
        driver.findElement(By.xpath("//android.widget.TextView[contains(@text,'Download Form')]")).click();
        WebElement popUp = driver.findElement(By.id("android:id/message"));
        assertEquals("Please enter the administrator passcode:", popUp.getText());
        driver.findElement(By.className("android.widget.EditText")).sendKeys("12345");
        driver.findElement(By.id("android:id/button1")).click();
        driver.findElement(By.className("android.widget.EditText")).sendKeys("3379117");
        driver.findElement(By.id("android:id/button1")).click();

        //Back to main menu
        driver.findElement(By.id("android:id/up")).click();


        //Select test form
        driver.findElement(By.xpath("//android.widget.TextView[contains(@text,'1.8.0 Final UAT All Questions')]")).click();

        driver.findElement(By.id("org.akvo.flow:id/new_record")).click();
        driver.findElement(By.id("org.akvo.flow:id/survey_icon")).click();
        driver.findElement(By.xpath("//android.widget.TextView[contains(@text,'Submit')]")).click();

        //Confirm users can't submit empty forms
        WebElement submitButton = driver.findElement(By.xpath("//android.widget.Button[contains(@text,'Submit')]"));
        assertTrue(!submitButton.isEnabled());
    }

    @After
    public void tearDown() throws Exception {
        driver.quit();
    }
}
