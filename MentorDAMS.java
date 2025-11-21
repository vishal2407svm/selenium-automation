import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;

public class DAMSMentor {

    private static final String PHONE_NUMBER = "+919411611466";
    private static final String OTP = "2000";
    private static final int NUMBER_OF_BOOKINGS = 5;

    private static WebDriver driver;
    private static WebDriverWait wait;
    private static JavascriptExecutor js;

    private static List<String> logMessages = new ArrayList<>();
    private static List<String> screenshots = new ArrayList<>();
    private static SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private static SimpleDateFormat fileFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");

    public static void main(String[] args) {

        try {
            new File("screenshots").mkdirs();
            log("===== DAMS MENTOR DESK AUTOMATION STARTED =====");

            setupDriver();
            login();
            navigateToMentorDesk();
            bookRandomDifferentSessions();

            log("✔ Automation Completed Successfully");

        } catch (Exception e) {
            log("❌ FATAL ERROR: " + e.getMessage());
            captureScreenshot("fatal_error");
        } finally {
            generateReport();
            sleep(3);
            if (driver != null) driver.quit();
        }
    }

    private static void setupDriver() {
        log("🔧 Setting up Chrome driver...");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-notifications");

        boolean isCI = System.getenv("CI") != null;

        if (isCI) {
            log("Running in GitHub Actions (Headless Mode Enabled)");
            options.addArguments("--headless");            // ← FIXED
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
        } else {
            options.addArguments("--start-maximized");
            if (new File("chromedriver.exe").exists()) {
                System.setProperty("webdriver.chrome.driver", "chromedriver.exe");
            }
        }

        // ❗ IMPORTANT: ChromeDriver PATH SET KRNE KI ZARURAT NAHI  
        // GitHub Actions automatically installs chromedriver correctly

        driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        js = (JavascriptExecutor) driver;

        log("✔ Chrome Driver Ready");
    }

    private static void login() {
        try {
            log("🔐 Login process starting...");
            driver.get("https://www.damsdelhi.com/");
            sleep(3);

            WebElement signIn = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[contains(text(),'Sign')] | //a[contains(text(),'Sign')]")));
            jsClick(signIn);

            sleep(2);

            WebElement phone = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//input[@type='tel']")));
            phone.sendKeys(PHONE_NUMBER);

            WebElement otpBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.className("common-bottom-btn")));
            jsClick(otpBtn);

            sleep(3);

            handleYesPopup();

            WebElement otp = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//input[@type='text']")));
            otp.sendKeys(OTP);

            WebElement submit = wait.until(ExpectedConditions.elementToBeClickable(
                By.className("common-bottom-btn")));
            jsClick(submit);

            sleep(5);

            log("✔ Login Successful");

        } catch (Exception e) {
            captureScreenshot("login_error");
            throw new RuntimeException("Login failed", e);
        }
    }

    private static void navigateToMentorDesk() {
        try {
            log("➡ Navigating to Mentor Desk...");
            driver.get("https://www.damsdelhi.com/mentor-desk");
            sleep(3);
            log("✔ Mentor Desk Opened");

        } catch (Exception e) {
            captureScreenshot("navigation_error");
            throw new RuntimeException("Navigation failed", e);
        }
    }

    private static void bookRandomDifferentSessions() {
        try {
            log("📚 Booking random sessions...");

            List<WebElement> buttons = findAllBookOnlineButtons();
            if (buttons.isEmpty()) {
                log("❌ No Book Online buttons found");
                return;
            }

            int count = Math.min(NUMBER_OF_BOOKINGS, buttons.size());
            Collections.shuffle(buttons);

            for (int i = 0; i < count; i++) {
                log("➡ Booking: " + (i + 1));

                WebElement btn = buttons.get(i);
                jsClick(btn);

                sleep(5);
                captureScreenshot("booking_" + (i + 1));

                completeCheckout(i + 1);

                navigateBackToMentorDesk();
            }

        } catch (Exception e) {
            captureScreenshot("booking_error");
        }
    }

    private static List<WebElement> findAllBookOnlineButtons() {
        return driver.findElements(
                By.xpath("//button[contains(text(),'Book Online') or contains(text(),'Book online')]"));
    }

    private static void completeCheckout(int num) {
        try {
            clickContinueIfPresent();
            clickPlaceOrderButton();

            sleep(3);
            captureScreenshot("checkout_" + num);

        } catch (Exception e) {
            captureScreenshot("checkout_failed_" + num);
        }
    }

    private static void clickContinueIfPresent() {
        try {
            List<WebElement> list = driver.findElements(
                By.xpath("//button[contains(text(),'Continue')]"));
            if (!list.isEmpty()) jsClick(list.get(0));
        } catch (Exception ignored) {}
    }

    private static void clickPlaceOrderButton() {
        WebElement btn = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[contains(@class,'btn-danger')]")));
        jsClick(btn);
    }

    private static void navigateBackToMentorDesk() {
        driver.get("https://www.damsdelhi.com/mentor-desk");
        sleep(2);
    }

    private static void jsClick(WebElement el) {
        js.executeScript("arguments[0].click();", el);
    }

    private static void log(String m) {
        String msg = "[" + timeFormat.format(new Date()) + "] " + m;
        System.out.println(msg);
        logMessages.add(msg);
    }

    private static void captureScreenshot(String name) {
        try {
            String finalName = "screenshots/" + name + "_" + fileFormat.format(new Date()) + ".png";
            File scr = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Files.copy(scr.toPath(), new File(finalName).toPath(), StandardCopyOption.REPLACE_EXISTING);
            screenshots.add(finalName);
        } catch (Exception ignored) {}
    }

    private static void sleep(int s) {
        try { Thread.sleep(s * 1000L); } catch (Exception ignored) {}
    }

    private static void generateReport() {
        try {
            String fname = "DAMS_Report_" + fileFormat.format(new Date()) + ".html";
            FileWriter w = new FileWriter(fname);
            w.write("<html><body><h1>DAMS Automation Report</h1><ul>");
            for (String m : logMessages) w.write("<li>" + m + "</li>");
            w.write("</ul></body></html>");
            w.close();
        } catch (Exception ignored) {}
    }
}
