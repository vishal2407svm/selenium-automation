import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;

public class DAMSMentor {
    
    // Configuration - These values are replaced by SED commands in YAML
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
    private static Set<String> bookedCourseNames = new HashSet<>();
    
    public static void main(String[] args) {
        try {
            // Create screenshots directory
            new File("screenshots").mkdirs();
            
            log("╔═══════════════════════════════════════════╗");
            log("║   DAMS MENTOR DESK - RANDOM BOOKINGS        ║");
            log("╚═══════════════════════════════════════════╝");
            log("");
            log("📋 Configuration:");
            log("  Phone: " + PHONE_NUMBER);
            log("  Target Bookings: " + NUMBER_OF_BOOKINGS);
            log("  Environment: " + (System.getenv("CI") != null ? "GitHub Actions" : "Local"));
            log("");
            
            setupDriver();
            login();
            navigateToMentorDesk();
            bookRandomDifferentSessions();
            
            log("");
            log("✅ Automation completed successfully!");
            
        } catch (Exception e) {
            log("❌ FATAL ERROR: " + e.getMessage());
            e.printStackTrace();
            captureScreenshot("fatal_error");
            // Don't exit with 1 immediately so report generates, but mark failure
        } finally {
            generateReport();
            log("");
            log("🔒 Closing browser...");
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }
    
    private static void setupDriver() {
        log("🔧 Setting up Chrome driver...");
        
        boolean isCI = System.getenv("CI") != null;
        
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");
        
        if (isCI) {
            // GitHub Actions specific setup
            log("⚙️ Configuring for CI/Headless environment");
            options.addArguments("--headless=new"); // New headless mode for better stability
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--disable-gpu");
            options.addArguments("--disable-extensions");
            
            // Point to the pre-installed ChromeDriver in GitHub Actions
            // Usually at /usr/bin/chromedriver or /usr/local/bin/chromedriver
            File usrBinDriver = new File("/usr/bin/chromedriver");
            File usrLocalBinDriver = new File("/usr/local/bin/chromedriver");
            
            if (usrLocalBinDriver.exists()) {
                System.setProperty("webdriver.chrome.driver", usrLocalBinDriver.getAbsolutePath());
                log("✅ Using driver at: " + usrLocalBinDriver.getAbsolutePath());
            } else if (usrBinDriver.exists()) {
                System.setProperty("webdriver.chrome.driver", usrBinDriver.getAbsolutePath());
                log("✅ Using driver at: " + usrBinDriver.getAbsolutePath());
            } else {
                log("⚠️ Could not find standard chromedriver path, relying on System PATH");
            }
        } else {
            options.addArguments("--start-maximized");
            // Local setup
            String localDriver = "chromedriver.exe"; // Windows assumption
            if (new File(localDriver).exists()) {
                System.setProperty("webdriver.chrome.driver", localDriver);
            }
        }
        
        try {
            driver = new ChromeDriver(options);
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            wait = new WebDriverWait(driver, Duration.ofSeconds(30));
            js = (JavascriptExecutor) driver;
            log("✅ Driver initialized successfully");
        } catch (Exception e) {
            log("❌ Failed to initialize driver: " + e.getMessage());
            throw e;
        }
    }
    
    private static void login() {
        log("");
        log("🔐 Starting login process...");
        
        try {
            driver.get("https://www.damsdelhi.com/");
            log("✅ Loaded damsdelhi.com");
            
            // Check if already logged in (rare but possible)
            if (driver.getPageSource().contains("Logout") || driver.getPageSource().contains("Sign Out")) {
                log("ℹ️ Already logged in");
                return;
            }

            // Smart wait for Sign In button
            WebElement signInBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[contains(text(), 'Sign in') or contains(text(), 'Sign In') or contains(@class, 'signin')]")));
            jsClick(signInBtn);
            log("✅ Clicked Sign In");
            
            // Phone Input
            WebElement phoneInput = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//input[@type='tel' or @name='mobile' or contains(@placeholder, 'Mobile')]")));
            phoneInput.clear();
            phoneInput.sendKeys(PHONE_NUMBER);
            log("✅ Entered phone number");
            
            // Request OTP
            WebElement requestOtpBtn = driver.findElement(By.xpath("//button[contains(text(), 'OTP')]"));
            jsClick(requestOtpBtn);
            log("✅ Clicked Request OTP");
            
            // Handle "Logout from other device" popup if it appears
            handleYesPopup();
            
            // OTP Input
            WebElement otpInput = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//input[contains(@placeholder, 'OTP') or @name='otp']")));
            otpInput.clear();
            otpInput.sendKeys(OTP);
            log("✅ Entered OTP");
            
            // Submit
            WebElement submitBtn = driver.findElement(By.xpath("//button[contains(text(), 'Verify') or contains(text(), 'Submit')]"));
            jsClick(submitBtn);
            log("✅ Submitted OTP");
            
            sleep(5);
            log("✅ Login sequence completed");
            
        } catch (Exception e) {
            log("❌ Login failed: " + e.getMessage());
            captureScreenshot("login_error");
            throw new RuntimeException("Login failed", e);
        }
    }
    
    private static void navigateToMentorDesk() {
        log("");
        log("🍔 Navigating to Mentor Desk...");
        
        try {
            // Try direct URL first (More reliable)
            driver.get("https://www.damsdelhi.com/mentor-desk"); 
            sleep(3);
            
            if (driver.getCurrentUrl().contains("mentor-desk")) {
                log("✅ Navigated via Direct URL");
                return;
            }
            
            // Fallback to UI navigation
            WebElement hamburger = wait.until(ExpectedConditions.elementToBeClickable(By.className("humburgerIcon")));
            jsClick(hamburger);
            sleep(2);
            
            WebElement mentorLink = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//a[contains(text(), 'Mentor Desk') or contains(@href, 'mentor-desk')]")));
            jsClick(mentorLink);
            
            log("✅ Navigated via Menu");
            sleep(5);
            
        } catch (Exception e) {
            // Often fails if not logged in properly, but let's try to continue or re-login?
            log("⚠️ Navigation issue: " + e.getMessage());
            // Try clicking the menu item via JS hard force
            js.executeScript("window.location.href = 'https://www.damsdelhi.com/mentor-desk';");
            sleep(5);
        }
    }
    
    private static void bookRandomDifferentSessions() {
        log("");
        log("📚 Starting booking loop...");
        
        int successfulBookings = 0;
        int maxLoops = 10; 
        int loops = 0;

        while (successfulBookings < NUMBER_OF_BOOKINGS && loops < maxLoops) {
            loops++;
            log("--- Loop " + loops + " ---");
            
            // Ensure we are on mentor desk
            if (!driver.getCurrentUrl().contains("mentor-desk")) {
                driver.get("https://www.damsdelhi.com/mentor-desk");
                sleep(3);
            }

            // Scroll to load items
            scrollToBottom();
            
            // Find all "Book Online" buttons
            List<WebElement> buttons = driver.findElements(By.xpath("//button[contains(text(), 'Book Online')]"));
            
            if (buttons.isEmpty()) {
                log("❌ No Booking buttons found on page!");
                break;
            }
            
            boolean bookedInThisLoop = false;
            
            for (WebElement btn : buttons) {
                try {
                    String courseName = extractCourseName(btn);
                    
                    if (bookedCourseNames.contains(courseName)) {
                        continue; // Already booked this one
                    }
                    
                    log("Attempting: " + courseName);
                    
                    // Click Book
                    jsClick(btn);
                    sleep(5);
                    
                    // Handle details page
                    if (processBooking(courseName, successfulBookings + 1)) {
                        successfulBookings++;
                        bookedCourseNames.add(courseName);
                        bookedInThisLoop = true;
                        break; // Break inner loop to refresh list
                    } else {
                        driver.navigate().back(); // Go back if failed
                        sleep(3);
                    }
                    
                } catch (StaleElementReferenceException e) {
                    // DOM updated, need to refresh list
                    break;
                } catch (Exception e) {
                    log("⚠️ Error with button: " + e.getMessage());
                }
            }
            
            if (!bookedInThisLoop) {
                log("⚠️ Could not book any new course in this pass.");
            }
        }
        
        log("📊 Final Count: " + successfulBookings + "/" + NUMBER_OF_BOOKINGS);
    }
    
    private static boolean processBooking(String courseName, int index) {
        try {
            // Scroll down to find Buy/Pay button
            scrollToBottom();
            
            // Look for the main action button (Buy Now / Pay)
            WebElement actionBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[contains(text(), 'Buy') or contains(text(), 'Pay') or contains(@class, 'btn-danger')]")));
            
            captureScreenshot("booking_start_" + index);
            jsClick(actionBtn);
            sleep(3);
            
            // Handle potential "Are you sure?" or "Continue" popups
            handleYesPopup();
            clickContinueIfPresent();
            handleYesPopup();
            
            // Final "Place Order" or "Pay Now"
            try {
                WebElement placeOrder = driver.findElement(By.xpath("//button[contains(text(), 'Place Order') or contains(text(), 'Pay Now')]"));
                jsClick(placeOrder);
                sleep(5);
            } catch (Exception e) {
                // Might have already clicked it in previous step
            }
            
            // Wait for Payment Gateway (QR Code or Paytm)
            log("⏳ Waiting for payment gateway...");
            sleep(10);
            
            captureScreenshot("payment_qr_" + index + "_" + sanitize(courseName));
            log("✅ Captured QR Code for: " + courseName);
            
            // Close Payment Window (Escape logic)
            closePaymentModal();
            
            return true;
        } catch (Exception e) {
            log("❌ Booking process failed for " + courseName + ": " + e.getMessage());
            captureScreenshot("fail_" + index);
            return false;
        }
    }
    
    // Helper Methods
    
    private static void jsClick(WebElement el) {
        js.executeScript("arguments[0].click();", el);
    }
    
    private static void handleYesPopup() {
        try {
            List<WebElement> yesBtns = driver.findElements(By.xpath("//button[contains(text(), 'Yes') or .//span[contains(text(), 'Yes')]]"));
            for (WebElement btn : yesBtns) {
                if (btn.isDisplayed()) {
                    jsClick(btn);
                    sleep(1);
                }
            }
        } catch (Exception e) {}
    }
    
    private static void clickContinueIfPresent() {
         try {
            List<WebElement> btns = driver.findElements(By.xpath("//button[contains(text(), 'Continue')]"));
            for (WebElement btn : btns) {
                if (btn.isDisplayed()) {
                    jsClick(btn);
                    sleep(1);
                }
            }
        } catch (Exception e) {}
    }
    
    private static void closePaymentModal() {
        // Try pressing Escape
        try {
            new org.openqa.selenium.interactions.Actions(driver).sendKeys(Keys.ESCAPE).perform();
        } catch (Exception e) {}
        
        // Try finding close buttons
        try {
            List<WebElement> closeBtns = driver.findElements(By.xpath("//span[contains(@class, 'close')] | //button[contains(text(), 'Cancel')]"));
            for (WebElement btn : closeBtns) {
                if (btn.isDisplayed()) jsClick(btn);
            }
        } catch (Exception e) {}
    }

    private static String extractCourseName(WebElement btn) {
        // Try to find a header near the button
        try {
            return btn.findElement(By.xpath("./ancestor::div[contains(@class,'card')]//h3")).getText();
        } catch (Exception e) {
            return "Course_" + System.currentTimeMillis();
        }
    }

    private static void scrollToBottom() {
        try {
            long lastHeight = (Long) js.executeScript("return document.body.scrollHeight");
            while (true) {
                js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
                sleep(2);
                long newHeight = (Long) js.executeScript("return document.body.scrollHeight");
                if (newHeight == lastHeight) break;
                lastHeight = newHeight;
            }
            // Scroll back top a bit to ensure headers are visible
             js.executeScript("window.scrollTo(0, 0);");
        } catch (Exception e) {}
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9]", "_");
    }

    private static void log(String msg) {
        String entry = "[" + timeFormat.format(new Date()) + "] " + msg;
        System.out.println(entry);
        logMessages.add(entry);
    }
    
    private static void sleep(int seconds) {
        try { Thread.sleep(seconds * 1000L); } catch (InterruptedException e) {}
    }
    
    private static void captureScreenshot(String name) {
        try {
            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            File dest = new File("screenshots/" + name + ".png");
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            screenshots.add(dest.getPath());
        } catch (Exception e) {
            log("Screenshot failed: " + e.getMessage());
        }
    }

    private static void generateReport() {
        try {
            String filename = "DAMS_Report_" + fileFormat.format(new Date()) + ".html";
            StringBuilder html = new StringBuilder("<html><body><h1>Execution Report</h1><ul>");
            for (String msg : logMessages) {
                String color = msg.contains("❌") ? "red" : (msg.contains("✅") ? "green" : "black");
                html.append("<li style='color:").append(color).append("'>").append(msg).append("</li>");
            }
            html.append("</ul><h2>Screenshots</h2>");
            for (String path : screenshots) {
                html.append("<div><h4>").append(new File(path).getName()).append("</h4>");
                html.append("<img src='").append(path).append("' style='max-width:600px;border:1px solid #ccc;'/></div>");
            }
            html.append("</body></html>");
            
            FileWriter writer = new FileWriter(filename);
            writer.write(html.toString());
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
