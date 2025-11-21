import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

// Removed Commons IO import to avoid extra dependency download
// import org.apache.commons.io.FileUtils; 

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files; // Added Standard Java NIO
import java.nio.file.StandardCopyOption; // Added Standard Java NIO
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;

public class DAMSMentor {
    
    // Configuration
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
    private static Random random = new Random();
    
    // Track which courses have been booked by button index
    private static Set<Integer> bookedButtonIndices = new HashSet<>();
    
    public static void main(String[] args) {
        try {
            new File("screenshots").mkdirs();
            
            log("╔═══════════════════════════════════════════╗");
            log("║   DAMS MENTOR DESK - RANDOM 5 BOOKINGS    ║");
            log("╚═══════════════════════════════════════════╝");
            log("");
            
            // Check Environment
            String env = System.getenv("CI") != null ? "GitHub Actions" : "Local";
            log("📋 Configuration:");
            log("  Phone: " + PHONE_NUMBER);
            log("  Target Bookings: " + NUMBER_OF_BOOKINGS);
            log("  Environment: " + env);
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
            // Do not System.exit(1) here so report generates
        } finally {
            generateReport();
            log("");
            log("🔒 Closing browser in 5 seconds...");
            sleep(5);
            if (driver != null) {
                try { driver.quit(); } catch (Exception e) {}
            }
        }
    }
    
    private static void setupDriver() {
        log("🔧 Setting up Chrome driver...");
        
        // Detect if running on GitHub Actions
        boolean isCI = System.getenv("CI") != null;
        
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-notifications");
        
        if (isCI) {
            // --- GitHub Actions Settings ---
            log("⚙️ Configuring for Headless CI Environment");
            options.addArguments("--headless=new"); // Essential for CI
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--disable-gpu");
            
            // Use Linux Driver Paths
            File usrBinDriver = new File("/usr/bin/chromedriver");
            File usrLocalBinDriver = new File("/usr/local/bin/chromedriver");
            
            if (usrLocalBinDriver.exists()) {
                System.setProperty("webdriver.chrome.driver", usrLocalBinDriver.getAbsolutePath());
            } else if (usrBinDriver.exists()) {
                System.setProperty("webdriver.chrome.driver", usrBinDriver.getAbsolutePath());
            }
        } else {
            // --- Local Windows Settings ---
            options.addArguments("--start-maximized");
            // Only set property if chromedriver.exe exists locally
            if (new File("chromedriver.exe").exists()) {
                System.setProperty("webdriver.chrome.driver", "chromedriver.exe");
                log("✅ Found local chromedriver.exe");
            }
        }
        
        driver = new ChromeDriver(options);
        if (!isCI) {
            driver.manage().window().maximize();
        }
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        js = (JavascriptExecutor) driver;
        
        log("✅ Driver ready");
    }
    
    private static void login() {
        log("");
        log("🔐 Starting login process...");
        
        try {
            driver.get("https://www.damsdelhi.com/");
            sleep(3);
            log("✅ Loaded damsdelhi.com");
            
            // Click Sign in
            try {
                WebElement signInBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(text(), 'Sign in') or contains(text(), 'Sign In')]")));
                jsClick(signInBtn);
                log("✅ Clicked Sign In button");
            } catch (Exception e) {
                WebElement signInBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//a[contains(text(), 'Sign in') or contains(text(), 'Sign In')]")));
                jsClick(signInBtn);
                log("✅ Clicked Sign In link");
            }
            sleep(3);
            
            // Enter phone number
            WebElement phoneInput = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//input[@type='tel' or @type='number' or contains(@placeholder, 'number')]")));
            phoneInput.clear();
            phoneInput.sendKeys(PHONE_NUMBER);
            log("✅ Entered phone number");
            sleep(2);
            
            // Request OTP
            WebElement otpBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.className("common-bottom-btn")));
            jsClick(otpBtn);
            log("✅ Clicked Request OTP");
            sleep(3);
            
            // Handle logout popup if present
            handleYesPopup();
            
            // Enter OTP
            WebElement otpInput = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//input[@type='text' or @type='number' or contains(@placeholder, 'OTP')]")));
            otpInput.clear();
            otpInput.sendKeys(OTP);
            log("✅ Entered OTP");
            sleep(2);
            
            // Submit OTP
            WebElement submitBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.className("common-bottom-btn")));
            jsClick(submitBtn);
            log("✅ Submitted OTP");
            sleep(5);
            
            log("✅ Login successful");
            
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
            // FAST METHOD: Try Direct URL first
            driver.get("https://www.damsdelhi.com/mentor-desk");
            sleep(3);
            
            if (driver.getCurrentUrl().contains("mentor-desk")) {
                log("✅ Navigated via Direct URL");
                return;
            }

            // Fallback to UI click
            WebElement hamburgerBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.className("humburgerIcon")));
            jsClick(hamburgerBtn);
            log("✅ Clicked hamburger menu");
            sleep(2);
            
            boolean clicked = clickMentorDeskElement();
            
            if (!clicked) {
                throw new RuntimeException("Could not find or click Mentor Desk element");
            }
            
            sleep(5);
            log("✅ Successfully navigated to Mentor Desk");
            
        } catch (Exception e) {
            log("❌ Navigation to Mentor Desk failed: " + e.getMessage());
            captureScreenshot("navigation_error");
            throw new RuntimeException("Navigation failed", e);
        }
    }
    
    private static boolean clickMentorDeskElement() {
        // Strategy 1: Find by exact text match
        List<WebElement> allElements = driver.findElements(By.xpath("//*"));
        
        for (WebElement element : allElements) {
            try {
                String text = element.getText();
                if (text != null && text.trim().equals("Mentor Desk") && element.isDisplayed()) {
                    js.executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
                    sleep(1);
                    
                    try {
                        element.click();
                        return true;
                    } catch (Exception e1) {
                        jsClick(element);
                        return true;
                    }
                }
            } catch (Exception e) {}
        }
        return false;
    }
    
    private static void bookRandomDifferentSessions() {
        log("");
        log("📚 Starting random booking process for " + NUMBER_OF_BOOKINGS + " different courses...");
        
        try {
            int successfulBookings = 0;
            scrollToLoadAllCards();
            
            List<WebElement> allBookButtons = findAllBookOnlineButtons();
            
            if (allBookButtons.isEmpty()) {
                log("❌ No booking buttons found!");
                return;
            }
            
            int targetBookings = Math.min(NUMBER_OF_BOOKINGS, allBookButtons.size());
            log("ℹ️  Will book " + targetBookings + " courses");
            
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < allBookButtons.size(); i++) indices.add(i);
            Collections.shuffle(indices);
            
            for (int i = 0; i < targetBookings; i++) {
                int buttonIndex = indices.get(i);
                
                log("");
                log("🎯 Booking course " + (successfulBookings + 1) + " of " + targetBookings);
                
                if (successfulBookings > 0) {
                    navigateBackToMentorDesk();
                    scrollToLoadAllCards();
                }
                
                allBookButtons = findAllBookOnlineButtons();
                
                if (buttonIndex >= allBookButtons.size()) buttonIndex = 0;
                
                String courseName = extractCourseNameFromButton(allBookButtons.get(buttonIndex), buttonIndex);
                log("📖 Selected: " + courseName);
                
                boolean bookingSuccess = bookCourseByButton(allBookButtons.get(buttonIndex), successfulBookings + 1, courseName);
                
                if (bookingSuccess) {
                    successfulBookings++;
                    bookedButtonIndices.add(buttonIndex);
                    log("✅ Successfully booked: " + courseName);
                } else {
                    log("⚠️  Booking failed, continuing...");
                }
            }
            
            log("");
            log("📊 SUMMARY: Successful bookings: " + successfulBookings + "/" + targetBookings);
            
        } catch (Exception e) {
            log("❌ Booking process error: " + e.getMessage());
            captureScreenshot("booking_process_error");
        }
    }
    
    private static void scrollToLoadAllCards() {
        log("⬇️  Scrolling to load all cards...");
        try {
            js.executeScript("window.scrollTo(0, 0);");
            sleep(1);
            long lastHeight = (Long) js.executeScript("return document.body.scrollHeight");
            int stableCount = 0;
            while (stableCount < 2) {
                js.executeScript("window.scrollBy(0, 800);");
                sleep(1);
                long newHeight = (Long) js.executeScript("return document.body.scrollHeight");
                if (newHeight == lastHeight) stableCount++;
                else {
                    stableCount = 0;
                    lastHeight = newHeight;
                }
            }
            js.executeScript("window.scrollTo(0, 0);");
        } catch (Exception e) {}
    }
    
    private static List<WebElement> findAllBookOnlineButtons() {
        List<WebElement> buttons = new ArrayList<>();
        try {
            List<WebElement> found = driver.findElements(
                By.xpath("//button[contains(text(), 'Book Online') or contains(text(), 'Book online')]"));
            for (WebElement btn : found) {
                if (btn.isDisplayed() && btn.isEnabled()) buttons.add(btn);
            }
        } catch (Exception e) {}
        return buttons;
    }
    
    private static String extractCourseNameFromButton(WebElement bookButton, int index) {
        try {
            WebElement parent = bookButton.findElement(By.xpath("./ancestor::div[contains(@class, 'col') or contains(@class, 'card')][1]"));
            WebElement heading = parent.findElement(By.xpath(".//h3[1] | .//h4[1] | .//h5[1]"));
            String courseName = heading.getText().trim().split("\n")[0];
            return courseName.length() > 50 ? courseName.substring(0, 50) : courseName;
        } catch (Exception e) {
            return "Course_" + (index + 1);
        }
    }
    
    private static boolean bookCourseByButton(WebElement bookButton, int bookingNumber, String courseName) {
        try {
            js.executeScript("arguments[0].scrollIntoView({block: 'center'});", bookButton);
            sleep(2);
            jsClick(bookButton);
            log("✅ Clicked 'Book Online'");
            sleep(5);
            
            js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
            sleep(2);
            
            captureScreenshot("booking_" + bookingNumber + "_" + sanitizeFileName(courseName));
            
            clickBuyTicketButton();
            completeCheckout(bookingNumber, courseName);
            return true;
        } catch (Exception e) {
            log("❌ Booking failed: " + e.getMessage());
            captureScreenshot("booking_error_" + bookingNumber);
            return false;
        }
    }
    
    private static String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_").substring(0, Math.min(name.length(), 30));
    }
    
    private static void clickBuyTicketButton() {
        try {
            // Improved selector strategy
            WebElement buyBtn = wait.until(ExpectedConditions.elementToBeClickable(
                 By.xpath("//button[contains(@class, 'btn') and not(contains(@class, 'danger')) and (contains(text(), 'Buy') or contains(text(), 'Ticket'))] | //button[@class='btn']")));
            
            js.executeScript("arguments[0].scrollIntoView({block: 'center'});", buyBtn);
            sleep(1);
            jsClick(buyBtn);
            log("✅ Clicked Buy button");
            sleep(3);
        } catch (Exception e) {
            throw new RuntimeException("Buy Ticket button not found/clickable");
        }
    }
    
    private static void completeCheckout(int bookingNumber, String courseName) {
        try {
            selectDurationIfAvailable();
            handleYesPopup();
            sleep(1);
            clickContinueIfPresent();
            handleYesPopup();
            sleep(1);
            clickPlaceOrderButton();
            completePayment(bookingNumber, courseName);
        } catch (Exception e) {
            throw new RuntimeException("Checkout failed", e);
        }
    }
    
    private static void selectDurationIfAvailable() {
        try {
            List<WebElement> durations = driver.findElements(By.xpath("//h3[contains(text(), 'Month')]"));
            if (!durations.isEmpty()) {
                jsClick(durations.get(0));
                log("✅ Selected duration");
                sleep(2);
            }
        } catch (Exception e) {}
    }
    
    private static void clickContinueIfPresent() {
         try {
            List<WebElement> btns = driver.findElements(By.xpath("//button[contains(text(), 'Continue') or contains(@class, 'BtnNewCreate')]"));
            for (WebElement btn : btns) if (btn.isDisplayed()) jsClick(btn);
        } catch (Exception e) {}
    }
    
    private static void clickPlaceOrderButton() {
        try {
            WebElement btn = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[contains(@class, 'btn-danger') and contains(@class, 'btn-block')]")));
            jsClick(btn);
            log("✅ Clicked Place Order");
            sleep(3);
        } catch (Exception e) {
             throw new RuntimeException("Place Order failed", e);
        }
    }
    
    private static void completePayment(int bookingNumber, String courseName) {
        try {
            // Select Paytm if exists (Generic logic)
            try {
                 List<WebElement> paytm = driver.findElements(By.xpath("//label[.//span[contains(text(), 'Paytm')]]"));
                 if(!paytm.isEmpty() && paytm.get(0).isDisplayed()) jsClick(paytm.get(0));
            } catch (Exception e) {}
            
            // Click Pay Now
            try {
                WebElement payBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(text(), 'Pay Now') or contains(text(), 'Place Order')]")));
                jsClick(payBtn);
            } catch(Exception e) {}

            log("⏳ Waiting 20 seconds for QR code...");
            sleep(20);
            captureScreenshot("payment_" + bookingNumber + "_" + sanitizeFileName(courseName));
            closePaymentWindow();
            
        } catch (Exception e) {
            log("⚠️ Payment step incomplete (check screenshot)");
        }
    }
    
    private static void handleYesPopup() {
        try {
            List<WebElement> yesBtns = driver.findElements(By.xpath("//button[contains(text(), 'Yes') or .//span[contains(text(), 'Yes')]]"));
            for (WebElement btn : yesBtns) if (btn.isDisplayed()) jsClick(btn);
        } catch (Exception e) {}
    }
    
    private static void closePaymentWindow() {
        // Generic close attempts
        try { new org.openqa.selenium.interactions.Actions(driver).sendKeys(Keys.ESCAPE).perform(); } catch (Exception e) {}
        try {
             List<WebElement> closeBtns = driver.findElements(By.xpath("//span[contains(@class, 'close') or contains(@class, 'cross')]"));
             for(WebElement btn : closeBtns) if(btn.isDisplayed()) jsClick(btn);
        } catch(Exception e) {}
    }
    
    private static void navigateBackToMentorDesk() {
        try {
            driver.get("https://www.damsdelhi.com/mentor-desk");
            sleep(3);
            handleYesPopup();
        } catch (Exception e) {
            log("⚠️ Navigation warning: " + e.getMessage());
        }
    }
    
    // --- HELPER FUNCTIONS ---
    
    private static void jsClick(WebElement el) {
        js.executeScript("arguments[0].click();", el);
    }
    
    private static void log(String message) {
        String timestamp = timeFormat.format(new Date());
        String logEntry = "[" + timestamp + "] " + message;
        System.out.println(logEntry);
        logMessages.add(logEntry);
    }
    
    private static String captureScreenshot(String fileName) {
        try {
            String timestamp = fileFormat.format(new Date());
            String fullFileName = "screenshots/" + fileName + "_" + timestamp + ".png";
            File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            // Replaced FileUtils with Standard Java NIO
            Files.copy(screenshot.toPath(), new File(fullFileName).toPath(), StandardCopyOption.REPLACE_EXISTING);
            screenshots.add(fullFileName);
            log("📸 Screenshot: " + fullFileName);
            return fullFileName;
        } catch (Exception e) {
            log("❌ Screenshot failed: " + e.getMessage());
            return null;
        }
    }
    
    private static void sleep(int seconds) {
        try { Thread.sleep(seconds * 1000L); } catch (InterruptedException e) {}
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
        } catch (Exception e) {}
    }
}
