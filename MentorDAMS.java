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

// ✅ NAME FIXED: Ab ye filename (MentorDAMS.java) se match karega
public class MentorDAMS {

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
            log("===== DAMS MENTOR AUTOMATION STARTED =====");
            log("Environment: " + (isCI() ? "GitHub Actions" : "Local"));

            setupDriver();
            login();
            navigateToMentorDesk();
            bookRandomDifferentSessions();

            log("✅ Automation Completed Successfully");

        } catch (Exception e) {
            log("❌ FATAL ERROR: " + e.getMessage());
            e.printStackTrace();
            captureScreenshot("fatal_error");
            System.exit(1);
        } finally {
            generateReport();
            sleep(2);
            if (driver != null) {
                driver.quit();
                log("Browser closed");
            }
        }
    }

    private static boolean isCI() {
        return System.getenv("CI") != null || 
               System.getenv("GITHUB_ACTIONS") != null;
    }

    private static void setupDriver() {
        log("🔧 Setting up Chrome driver...");

        ChromeOptions options = new ChromeOptions();
        
        // Basic options
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--disable-infobars");
        options.addArguments("--disable-extensions");
        
        // User agent
        options.addArguments("user-agent=Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        if (isCI()) {
            log("🤖 CI/CD Mode - Headless Enabled");
            options.addArguments("--headless=new");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--disable-software-rasterizer");
            options.addArguments("--disable-setuid-sandbox");
        } else {
            log("💻 Local Mode - GUI Enabled");
            options.addArguments("--start-maximized");
        }

        try {
            driver = new ChromeDriver(options);
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(15));
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
            wait = new WebDriverWait(driver, Duration.ofSeconds(30));
            js = (JavascriptExecutor) driver;
            log("✅ Chrome Driver Initialized");
        } catch (Exception e) {
            log("❌ Driver Setup Failed: " + e.getMessage());
            throw new RuntimeException("Cannot initialize ChromeDriver", e);
        }
    }

    private static void login() {
        try {
            log("🔐 Starting login process...");
            
            driver.get("https://www.damsdelhi.com/");
            sleep(4);
            captureScreenshot("01_homepage");
            log("Homepage loaded");

            // Click Sign In button
            WebElement signIn = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[contains(text(),'Sign')] | //a[contains(text(),'Sign')] | //*[contains(@class,'sign')]")));
            jsClick(signIn);
            sleep(2);
            captureScreenshot("02_signin_clicked");
            log("Sign In clicked");

            // Enter phone number
            WebElement phone = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//input[@type='tel' or @type='text' or contains(@placeholder,'Phone') or contains(@placeholder,'phone')]")));
            phone.clear();
            phone.sendKeys(PHONE_NUMBER);
            log("📱 Phone entered: " + PHONE_NUMBER);
            sleep(1);

            // Click Send OTP
            WebElement otpBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[contains(@class,'common-bottom-btn') or contains(text(),'Send') or contains(text(),'OTP') or contains(text(),'Get')]")));
            jsClick(otpBtn);
            sleep(4);
            captureScreenshot("03_otp_requested");
            log("OTP requested");

            // Handle Yes popup if exists
            handleYesPopup();

            // Enter OTP
            WebElement otpInput = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//input[@type='text' or @type='tel' or contains(@placeholder,'OTP') or contains(@placeholder,'otp')]")));
            otpInput.clear();
            otpInput.sendKeys(OTP);
            log("🔑 OTP entered: " + OTP);
            sleep(1);

            // Submit OTP
            WebElement submit = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[contains(@class,'common-bottom-btn') or contains(text(),'Submit') or contains(text(),'Verify') or contains(text(),'Login')]")));
            jsClick(submit);
            sleep(5);
            captureScreenshot("04_login_complete");

            log("✅ Login Successful");

        } catch (Exception e) {
            log("❌ Login Failed: " + e.getMessage());
            captureScreenshot("error_login");
            throw new RuntimeException("Login process failed", e);
        }
    }

    private static void handleYesPopup() {
        try {
            List<WebElement> yesButtons = driver.findElements(
                By.xpath("//button[contains(text(),'Yes') or contains(text(),'YES') or contains(text(),'yes')]"));
            if (!yesButtons.isEmpty()) {
                jsClick(yesButtons.get(0));
                sleep(1);
                log("✓ Popup handled");
            }
        } catch (Exception ignored) {}
    }

    private static void navigateToMentorDesk() {
        try {
            log("📚 Navigating to Mentor Desk...");
            driver.get("https://www.damsdelhi.com/mentor-desk");
            sleep(4);
            captureScreenshot("05_mentor_desk");
            log("✅ Mentor Desk loaded");

        } catch (Exception e) {
            log("❌ Navigation Failed: " + e.getMessage());
            captureScreenshot("error_navigation");
            throw new RuntimeException("Cannot reach Mentor Desk", e);
        }
    }

    private static void bookRandomDifferentSessions() {
        try {
            log("🎯 Starting booking process...");

            List<WebElement> buttons = findAllBookOnlineButtons();
            
            if (buttons.isEmpty()) {
                log("⚠️ No 'Book Online' buttons found");
                captureScreenshot("06_no_buttons");
                return;
            }

            log("📋 Found " + buttons.size() + " available sessions");
            int count = Math.min(NUMBER_OF_BOOKINGS, buttons.size());
            Collections.shuffle(buttons);

            for (int i = 0; i < count; i++) {
                try {
                    log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    log("📌 Booking " + (i + 1) + "/" + count);

                    WebElement btn = buttons.get(i);
                    scrollToElement(btn);
                    sleep(1);
                    jsClick(btn);

                    sleep(5);
                    captureScreenshot("booking_" + (i + 1) + "_clicked");

                    completeCheckout(i + 1);

                    log("✅ Booking " + (i + 1) + " completed");
                    
                    navigateBackToMentorDesk();
                    
                    // Refresh button list for next iteration
                    if (i < count - 1) {
                        sleep(2);
                        buttons = findAllBookOnlineButtons();
                        Collections.shuffle(buttons);
                    }

                } catch (Exception e) {
                    log("⚠️ Booking " + (i + 1) + " failed: " + e.getMessage());
                    captureScreenshot("booking_" + (i + 1) + "_failed");
                    navigateBackToMentorDesk();
                }
            }

            log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log("✅ All bookings completed");

        } catch (Exception e) {
            log("❌ Booking Process Error: " + e.getMessage());
            captureScreenshot("error_booking");
        }
    }

    private static List<WebElement> findAllBookOnlineButtons() {
        return driver.findElements(
            By.xpath("//button[contains(translate(text(),'BOOKONLINE','bookonline'),'book online')]"));
    }

    private static void completeCheckout(int num) {
        try {
            log("💳 Processing checkout #" + num);
            
            clickContinueIfPresent();
            sleep(2);
            
            clickPlaceOrderButton();
            sleep(3);
            
            captureScreenshot("checkout_" + num + "_done");

        } catch (Exception e) {
            log("⚠️ Checkout #" + num + " issue: " + e.getMessage());
            captureScreenshot("checkout_" + num + "_error");
        }
    }

    private static void clickContinueIfPresent() {
        try {
            List<WebElement> continueButtons = driver.findElements(
                By.xpath("//button[contains(translate(text(),'CONTINUE','continue'),'continue')]"));
            if (!continueButtons.isEmpty()) {
                jsClick(continueButtons.get(0));
                log("→ Continue clicked");
                sleep(1);
            }
        } catch (Exception ignored) {}
    }

    private static void clickPlaceOrderButton() {
        try {
            WebElement placeOrderBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[contains(@class,'btn-danger') or contains(translate(text(),'PLACEORDER','placeorder'),'place order')]")));
            jsClick(placeOrderBtn);
            log("→ Place Order clicked");
        } catch (Exception e) {
            log("⚠️ Place Order button not found");
        }
    }

    private static void navigateBackToMentorDesk() {
        try {
            driver.get("https://www.damsdelhi.com/mentor-desk");
            sleep(3);
            log("↩ Back to Mentor Desk");
        } catch (Exception e) {
            log("⚠️ Navigation back failed");
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
            try {
                el.click();
            } catch (Exception ex) {
                log("⚠️ Click failed on element");
            }
        }
    }

    private static void log(String msg) {
        String timestamp = "[" + timeFormat.format(new Date()) + "]";
        String fullMsg = timestamp + " " + msg;
        System.out.println(fullMsg);
        logMessages.add(fullMsg);
    }

    private static void captureScreenshot(String name) {
        try {
            String filename = "screenshots/" + name + "_" + fileFormat.format(new Date()) + ".png";
            File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Files.copy(screenshot.toPath(), new File(filename).toPath(), StandardCopyOption.REPLACE_EXISTING);
            screenshots.add(filename);
            log("📸 Screenshot: " + name);
        } catch (Exception e) {
            log("⚠️ Screenshot failed: " + name);
        }
    }

    private static void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException ignored) {}
    }

    private static void generateReport() {
        try {
            String reportName = "DAMS_Report_" + fileFormat.format(new Date()) + ".html";
            FileWriter writer = new FileWriter(reportName);
            
            writer.write("<!DOCTYPE html><html><head>");
            writer.write("<meta charset='UTF-8'>");
            writer.write("<title>DAMS Automation Report</title>");
            writer.write("<style>");
            writer.write("body { font-family: 'Segoe UI', Arial, sans-serif; margin: 40px; background: #f5f5f5; }");
            writer.write("h1 { color: #2c3e50; border-bottom: 3px solid #3498db; padding-bottom: 10px; }");
            writer.write(".summary { background: white; padding: 20px; border-radius: 8px; margin: 20px 0; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }");
            writer.write(".log-entry { padding: 8px; margin: 5px 0; background: white; border-left: 3px solid #3498db; }");
            writer.write(".success { border-left-color: #27ae60; }");
            writer.write(".error { border-left-color: #e74c3c; }");
            writer.write(".warning { border-left-color: #f39c12; }");
            writer.write("</style></head><body>");
            
            writer.write("<h1>🤖 DAMS Automation Report</h1>");
            writer.write("<div class='summary'>");
            writer.write("<p><strong>Generated:</strong> " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "</p>");
            writer.write("<p><strong>Total Logs:</strong> " + logMessages.size() + "</p>");
            writer.write("<p><strong>Screenshots:</strong> " + screenshots.size() + "</p>");
            writer.write("<p><strong>Environment:</strong> " + (isCI() ? "GitHub Actions (CI/CD)" : "Local Machine") + "</p>");
            writer.write("</div>");
            
            writer.write("<h2>📋 Execution Log</h2>");
            for (String log : logMessages) {
                String cssClass = "log-entry";
                if (log.contains("✅") || log.contains("✔")) cssClass += " success";
                else if (log.contains("❌")) cssClass += " error";
                else if (log.contains("⚠️")) cssClass += " warning";
                
                writer.write("<div class='" + cssClass + "'>" + log + "</div>");
            }
            
            writer.write("</body></html>");
            writer.close();
            
            log("📄 Report generated: " + reportName);
            
        } catch (Exception e) {
            log("⚠️ Report generation failed: " + e.getMessage());
        }
    }
}
