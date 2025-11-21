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
            e.printStackTrace();
            captureScreenshot("fatal_error");
        } finally {
            generateReport();
            sleep(3);
            if (driver != null) {
                driver.quit();
                log("Browser closed");
            }
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
        options.addArguments("--disable-popup-blocking");
        
        // User agent to avoid detection
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        boolean isCI = System.getenv("CI") != null || System.getenv("GITHUB_ACTIONS") != null;

        if (isCI) {
            log("Running in CI/CD Environment (Headless Mode)");
            options.addArguments("--headless=new");  // Updated headless syntax
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--disable-software-rasterizer");
        } else {
            log("Running in Local Environment");
            options.addArguments("--start-maximized");
        }

        driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(15));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
        wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        js = (JavascriptExecutor) driver;

        log("✔ Chrome Driver Ready");
    }

    private static void login() {
        try {
            log("🔐 Login process starting...");
            driver.get("https://www.damsdelhi.com/");
            sleep(3);
            captureScreenshot("homepage");

            // Sign In button click
            WebElement signIn = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[contains(text(),'Sign')] | //a[contains(text(),'Sign')] | //*[contains(text(),'Sign In')]")));
            jsClick(signIn);
            sleep(2);
            captureScreenshot("signin_clicked");

            // Phone number entry
            WebElement phone = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//input[@type='tel' or @type='text' or @placeholder='Phone']")));
            phone.clear();
            phone.sendKeys(PHONE_NUMBER);
            log("Phone number entered: " + PHONE_NUMBER);
            sleep(1);

            // Send OTP button
            WebElement otpBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[contains(@class,'common-bottom-btn') or contains(text(),'Send') or contains(text(),'OTP')]")));
            jsClick(otpBtn);
            sleep(3);
            captureScreenshot("otp_sent");

            // Handle popup if exists
            handleYesPopup();

            // OTP entry
            WebElement otp = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//input[@type='text' or @type='tel' or @placeholder='OTP']")));
            otp.clear();
            otp.sendKeys(OTP);
            log("OTP entered: " + OTP);
            sleep(1);

            // Submit button
            WebElement submit = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[contains(@class,'common-bottom-btn') or contains(text(),'Submit') or contains(text(),'Verify')]")));
            jsClick(submit);
            sleep(5);
            captureScreenshot("login_complete");

            log("✔ Login Successful");

        } catch (Exception e) {
            log("❌ Login Error: " + e.getMessage());
            captureScreenshot("login_error");
            throw new RuntimeException("Login failed: " + e.getMessage(), e);
        }
    }

    private static void handleYesPopup() {
        try {
            List<WebElement> yesButtons = driver.findElements(
                By.xpath("//button[contains(text(),'Yes') or contains(text(),'YES')]"));
            if (!yesButtons.isEmpty()) {
                jsClick(yesButtons.get(0));
                sleep(1);
                log("Popup handled");
            }
        } catch (Exception ignored) {}
    }

    private static void navigateToMentorDesk() {
        try {
            log("➡ Navigating to Mentor Desk...");
            driver.get("https://www.damsdelhi.com/mentor-desk");
            sleep(3);
            captureScreenshot("mentor_desk");
            log("✔ Mentor Desk Opened");

        } catch (Exception e) {
            log("❌ Navigation Error: " + e.getMessage());
            captureScreenshot("navigation_error");
            throw new RuntimeException("Navigation failed: " + e.getMessage(), e);
        }
    }

    private static void bookRandomDifferentSessions() {
        try {
            log("📚 Starting booking process...");

            List<WebElement> buttons = findAllBookOnlineButtons();
            if (buttons.isEmpty()) {
                log("❌ No Book Online buttons found");
                captureScreenshot("no_buttons_found");
                return;
            }

            log("Found " + buttons.size() + " booking buttons");
            int count = Math.min(NUMBER_OF_BOOKINGS, buttons.size());
            Collections.shuffle(buttons);

            for (int i = 0; i < count; i++) {
                try {
                    log("➡ Booking Session: " + (i + 1) + "/" + count);

                    WebElement btn = buttons.get(i);
                    scrollToElement(btn);
                    jsClick(btn);

                    sleep(4);
                    captureScreenshot("booking_" + (i + 1) + "_clicked");

                    completeCheckout(i + 1);

                    navigateBackToMentorDesk();
                    
                    // Re-find buttons after navigation
                    if (i < count - 1) {
                        buttons = findAllBookOnlineButtons();
                        Collections.shuffle(buttons);
                    }

                } catch (Exception e) {
                    log("⚠ Booking " + (i + 1) + " failed: " + e.getMessage());
                    captureScreenshot("booking_" + (i + 1) + "_failed");
                    navigateBackToMentorDesk();
                }
            }

            log("✔ Booking process completed");

        } catch (Exception e) {
            log("❌ Booking Error: " + e.getMessage());
            captureScreenshot("booking_error");
        }
    }

    private static List<WebElement> findAllBookOnlineButtons() {
        return driver.findElements(
                By.xpath("//button[contains(text(),'Book Online') or contains(text(),'Book online') or contains(text(),'BOOK ONLINE')]"));
    }

    private static void completeCheckout(int num) {
        try {
            log("Processing checkout for booking " + num);
            
            clickContinueIfPresent();
            sleep(2);
            
            clickPlaceOrderButton();
            sleep(3);
            
            captureScreenshot("checkout_complete_" + num);
            log("✔ Checkout " + num + " completed");

        } catch (Exception e) {
            log("⚠ Checkout " + num + " failed: " + e.getMessage());
            captureScreenshot("checkout_failed_" + num);
        }
    }

    private static void clickContinueIfPresent() {
        try {
            List<WebElement> list = driver.findElements(
                By.xpath("//button[contains(text(),'Continue') or contains(text(),'CONTINUE')]"));
            if (!list.isEmpty()) {
                jsClick(list.get(0));
                log("Continue button clicked");
            }
        } catch (Exception ignored) {}
    }

    private static void clickPlaceOrderButton() {
        try {
            WebElement btn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(@class,'btn-danger') or contains(text(),'Place Order') or contains(text(),'PLACE ORDER')]")));
            jsClick(btn);
            log("Place Order button clicked");
        } catch (Exception e) {
            log("⚠ Place Order button not found: " + e.getMessage());
        }
    }

    private static void navigateBackToMentorDesk() {
        try {
            driver.get("https://www.damsdelhi.com/mentor-desk");
            sleep(3);
            log("Navigated back to Mentor Desk");
        } catch (Exception e) {
            log("⚠ Navigation back failed: " + e.getMessage());
        }
    }

    private static void scrollToElement(WebElement element) {
        try {
            js.executeScript("arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'});", element);
            sleep(1);
        } catch (Exception ignored) {}
    }

    private static void jsClick(WebElement el) {
        try {
            js.executeScript("arguments[0].click();", el);
        } catch (Exception e) {
            el.click(); // Fallback to normal click
        }
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
            log("Screenshot saved: " + finalName);
        } catch (Exception e) {
            log("⚠ Screenshot failed: " + e.getMessage());
        }
    }

    private static void sleep(int s) {
        try { 
            Thread.sleep(s * 1000L); 
        } catch (Exception ignored) {}
    }

    private static void generateReport() {
        try {
            String fname = "DAMS_Report_" + fileFormat.format(new Date()) + ".html";
            FileWriter w = new FileWriter(fname);
            w.write("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>DAMS Report</title>");
            w.write("<style>body{font-family:Arial;margin:20px;} h1{color:#333;} li{margin:5px 0;}</style></head>");
            w.write("<body><h1>DAMS Automation Report</h1>");
            w.write("<p>Total Logs: " + logMessages.size() + "</p>");
            w.write("<p>Screenshots: " + screenshots.size() + "</p>");
            w.write("<ul>");
            for (String m : logMessages) {
                w.write("<li>" + m + "</li>");
            }
            w.write("</ul></body></html>");
            w.close();
            log("Report generated: " + fname);
        } catch (Exception e) {
            log("⚠ Report generation failed: " + e.getMessage());
        }
    }
}
