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
    
    // Configuration - Will be updated by GitHub Actions
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
            new File("screenshots").mkdirs();
            
            log("╔═══════════════════════════════════════════╗");
            log("║  DAMS MENTOR DESK - RANDOM 5 BOOKINGS    ║");
            log("╚═══════════════════════════════════════════╝");
            log("");
            log("📋 Configuration:");
            log("  Phone: " + PHONE_NUMBER);
            log("  Random bookings to make: " + NUMBER_OF_BOOKINGS);
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
            System.exit(1); // Exit with error code for GitHub Actions
        } finally {
            generateReport();
            log("");
            log("🔒 Closing browser in 5 seconds...");
            sleep(5);
            if (driver != null) {
                driver.quit();
            }
        }
    }
    
    private static void setupDriver() {
        log("🔧 Setting up Chrome driver...");
        
        // Check if running in CI environment
        boolean isCI = System.getenv("CI") != null;
        
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--no-sandbox");
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);
        
        if (isCI) {
            // GitHub Actions specific settings
            options.addArguments("--headless");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            log("✅ Running in headless mode (CI environment)");
        } else {
            // Local environment
            options.addArguments("--start-maximized");
            String driverPath = "chromedriver.exe";
            File driverFile = new File(driverPath);
            if (driverFile.exists()) {
                System.setProperty("webdriver.chrome.driver", driverPath);
                log("✅ Found chromedriver.exe in current directory");
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
            
            try {
                WebElement signInBtn = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//button[contains(text(), 'Sign in') or contains(text(), 'Sign In')]")));
                js.executeScript("arguments[0].click();", signInBtn);
                log("✅ Clicked Sign In button");
                sleep(3);
            } catch (Exception e) {
                WebElement signInBtn = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//a[contains(text(), 'Sign in') or contains(text(), 'Sign In')]")));
                js.executeScript("arguments[0].click();", signInBtn);
                log("✅ Clicked Sign In link");
                sleep(3);
            }
            
            WebElement phoneInput = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//input[@type='tel' or @type='number' or contains(@placeholder, 'number')]")));
            phoneInput.clear();
            phoneInput.sendKeys(PHONE_NUMBER);
            log("✅ Entered phone number");
            sleep(2);
            
            WebElement otpBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.className("common-bottom-btn")));
            js.executeScript("arguments[0].click();", otpBtn);
            log("✅ Clicked Request OTP");
            sleep(3);
            
            try {
                WebElement logoutBtn = driver.findElement(
                    By.xpath("//button[contains(@class, 'btndata') and contains(text(), 'Logout')]"));
                js.executeScript("arguments[0].click();", logoutBtn);
                log("✅ Clicked Logout popup");
                sleep(3);
            } catch (Exception e) {
                log("ℹ️  No logout popup");
            }
            
            WebElement otpInput = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//input[@type='text' or @type='number' or contains(@placeholder, 'OTP')]")));
            otpInput.clear();
            otpInput.sendKeys(OTP);
            log("✅ Entered OTP");
            sleep(2);
            
            WebElement submitBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.className("common-bottom-btn")));
            js.executeScript("arguments[0].click();", submitBtn);
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
            WebElement hamburgerBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.className("humburgerIcon")));
            js.executeScript("arguments[0].click();", hamburgerBtn);
            log("✅ Clicked hamburger menu");
            sleep(3);
            
            log("🔍 Searching for Mentor Desk in navigation...");
            
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
        List<WebElement> allElements = driver.findElements(By.xpath("//*[contains(text(), 'Mentor Desk')]"));
        
        for (WebElement element : allElements) {
            try {
                if (element.isDisplayed() && element.getText().trim().equals("Mentor Desk")) {
                    log("✅ Found Mentor Desk element: " + element.getTagName());
                    js.executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
                    sleep(1);
                    
                    try {
                        element.click();
                        log("✅ Clicked using standard click");
                        return true;
                    } catch (Exception e1) {
                        try {
                            js.executeScript("arguments[0].click();", element);
                            log("✅ Clicked using JavaScript");
                            return true;
                        } catch (Exception e2) {
                            WebElement parent = element.findElement(By.xpath("./.."));
                            js.executeScript("arguments[0].click();", parent);
                            log("✅ Clicked parent element");
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
            }
        }
        
        log("⚠️  Using JavaScript approach...");
        String jsScript = 
            "var elements = document.querySelectorAll('*');" +
            "for(var i = 0; i < elements.length; i++) {" +
            "  if(elements[i].textContent.trim() === 'Mentor Desk' && " +
            "     elements[i].offsetParent !== null) {" +
            "    elements[i].click();" +
            "    return true;" +
            "  }" +
            "}" +
            "return false;";
        
        Boolean jsClicked = (Boolean) js.executeScript(jsScript);
        if (jsClicked) {
            log("✅ Clicked using pure JavaScript");
            return true;
        }
        
        return false;
    }
    
    private static void bookRandomDifferentSessions() {
        log("");
        log("📚 Starting random booking process for " + NUMBER_OF_BOOKINGS + " different courses...");
        
        try {
            int successfulBookings = 0;
            int attempts = 0;
            int maxAttempts = NUMBER_OF_BOOKINGS * 2;
            
            while (successfulBookings < NUMBER_OF_BOOKINGS && attempts < maxAttempts) {
                attempts++;
                
                log("");
                log("═══════════════════════════════════════");
                log("🎯 Attempt " + attempts + " - Booking " + (successfulBookings + 1) + " of " + NUMBER_OF_BOOKINGS);
                log("═══════════════════════════════════════");
                
                if (successfulBookings > 0) {
                    navigateBackToMentorDesk();
                }
                
                scrollToLoadAllCards();
                
                List<WebElement> allBookButtons = findAllBookOnlineButtons();
                
                if (allBookButtons.isEmpty()) {
                    log("❌ No booking buttons found!");
                    break;
                }
                
                log("ℹ️  Found " + allBookButtons.size() + " total courses available");
                
                String selectedCourseName = null;
                WebElement selectedButton = null;
                
                for (WebElement button : allBookButtons) {
                    try {
                        String courseName = extractCourseNameFromButton(button);
                        if (!bookedCourseNames.contains(courseName)) {
                            selectedCourseName = courseName;
                            selectedButton = button;
                            break;
                        }
                    } catch (StaleElementReferenceException e) {
                        continue;
                    }
                }
                
                if (selectedButton == null || selectedCourseName == null) {
                    log("⚠️  All available courses already booked or no new courses found");
                    break;
                }
                
                log("📖 Selected: " + selectedCourseName);
                
                boolean bookingSuccess = bookCourseByButton(selectedButton, successfulBookings + 1, selectedCourseName);
                
                if (bookingSuccess) {
                    successfulBookings++;
                    bookedCourseNames.add(selectedCourseName);
                    log("✅ Successfully booked course " + successfulBookings + ": " + selectedCourseName);
                } else {
                    log("⚠️  Booking failed, will try another course...");
                }
            }
            
            log("");
            log("═══════════════════════════════════════");
            log("📊 BOOKING SUMMARY");
            log("═══════════════════════════════════════");
            log("✅ Successful bookings: " + successfulBookings + "/" + NUMBER_OF_BOOKINGS);
            log("📋 Total attempts: " + attempts);
            
        } catch (Exception e) {
            log("❌ Booking process error: " + e.getMessage());
            e.printStackTrace();
            captureScreenshot("booking_process_error");
        }
    }
    
    private static void scrollToLoadAllCards() {
        log("⬇️  Scrolling to load all course cards...");
        
        try {
            js.executeScript("window.scrollTo(0, 0);");
            sleep(2);
            
            long lastHeight = (Long) js.executeScript("return document.body.scrollHeight");
            int stableCount = 0;
            
            while (stableCount < 3) {
                js.executeScript("window.scrollBy(0, 800);");
                sleep(2);
                
                long newHeight = (Long) js.executeScript("return document.body.scrollHeight");
                if (newHeight == lastHeight) {
                    stableCount++;
                } else {
                    stableCount = 0;
                    lastHeight = newHeight;
                }
            }
            
            js.executeScript("window.scrollTo(0, 0);");
            sleep(2);
            
            log("✅ Finished scrolling");
            
        } catch (Exception e) {
            log("⚠️  Scroll warning: " + e.getMessage());
        }
    }
    
    private static List<WebElement> findAllBookOnlineButtons() {
        log("🔍 Finding all 'Book Online' buttons...");
        
        List<WebElement> buttons = new ArrayList<>();
        
        try {
            wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//button[contains(text(), 'Book Online') or contains(text(), 'Book online')]")));
            
            List<WebElement> found = driver.findElements(
                By.xpath("//button[contains(text(), 'Book Online') or contains(text(), 'Book online')]"));
            
            for (WebElement btn : found) {
                try {
                    if (btn.isDisplayed() && btn.isEnabled()) {
                        buttons.add(btn);
                    }
                } catch (StaleElementReferenceException e) {
                }
            }
            
            log("✅ Found " + buttons.size() + " available booking buttons");
            
        } catch (Exception e) {
            log("⚠️  Error finding buttons: " + e.getMessage());
        }
        
        return buttons;
    }
    
    private static String extractCourseNameFromButton(WebElement bookButton) {
        try {
            WebElement parent = bookButton.findElement(
                By.xpath("./ancestor::div[contains(@class, 'col') or contains(@class, 'card')][1]"));
            
            try {
                WebElement heading = parent.findElement(By.xpath(".//h3[1] | .//h4[1] | .//h5[1]"));
                String courseName = heading.getText().trim();
                
                if (courseName.contains("\n")) {
                    courseName = courseName.split("\n")[0];
                }
                
                if (courseName.length() > 50) {
                    courseName = courseName.substring(0, 50);
                }
                
                return courseName.isEmpty() ? "Course_" + System.currentTimeMillis() : courseName;
                
            } catch (Exception e) {
                return "Course_" + System.currentTimeMillis();
            }
            
        } catch (Exception e) {
            return "Course_" + System.currentTimeMillis();
        }
    }
    
    private static boolean bookCourseByButton(WebElement bookButton, int bookingNumber, String courseName) {
        try {
            js.executeScript("arguments[0].scrollIntoView({block: 'center'});", bookButton);
            sleep(2);
            
            try {
                bookButton.click();
            } catch (Exception e) {
                js.executeScript("arguments[0].click();", bookButton);
            }
            log("✅ Clicked 'Book Online' button");
            sleep(5);
            
            log("⬇️  Scrolling to load Buy button...");
            js.executeScript("window.scrollTo(0, 0);");
            sleep(1);
            
            for (int i = 0; i < 8; i++) {
                js.executeScript("window.scrollBy(0, 400);");
                sleep(1);
            }
            
            js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
            sleep(2);
            
            captureScreenshot("booking_" + bookingNumber + "_" + sanitizeFileName(courseName));
            
            clickBuyTicketButton();
            completeCheckout(bookingNumber, courseName);
            
            return true;
            
        } catch (Exception e) {
            log("❌ Booking failed: " + e.getMessage());
            e.printStackTrace();
            captureScreenshot("booking_error_" + bookingNumber);
            return false;
        }
    }
    
    private static String sanitizeFileName(String name) {
        String sanitized = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        return sanitized.substring(0, Math.min(sanitized.length(), 30));
    }
    
    private static void clickBuyTicketButton() {
        log("🎫 Clicking Buy Ticket button...");
        
        try {
            WebElement buyBtn = null;
            
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//button[@class='btn']")));
            
            By[] buttonSelectors = {
                By.xpath("//button[@class='btn']"),
                By.xpath("//button[contains(@class, 'btn') and not(contains(@class, 'btn-danger'))]"),
                By.cssSelector("button.btn")
            };
            
            for (By selector : buttonSelectors) {
                try {
                    List<WebElement> buttons = driver.findElements(selector);
                    
                    for (WebElement btn : buttons) {
                        try {
                            if (btn.isDisplayed() && btn.isEnabled()) {
                                buyBtn = btn;
                                break;
                            }
                        } catch (Exception e) {
                        }
                    }
                    
                    if (buyBtn != null) break;
                } catch (Exception e) {
                }
            }
            
            if (buyBtn == null) {
                throw new RuntimeException("Could not find Buy Ticket button");
            }
            
            js.executeScript("arguments[0].scrollIntoView({block: 'center'});", buyBtn);
            sleep(2);
            
            try {
                buyBtn.click();
                log("✅ Clicked Buy button");
            } catch (Exception e) {
                js.executeScript("arguments[0].click();", buyBtn);
                log("✅ Clicked Buy button (JS)");
            }
            
            sleep(3);
            
        } catch (Exception e) {
            log("❌ Buy Ticket button click failed: " + e.getMessage());
            throw new RuntimeException("Buy Ticket failed", e);
        }
    }
    
    private static void completeCheckout(int bookingNumber, String courseName) {
        log("💳 Completing checkout...");
        
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
            log("❌ Checkout failed: " + e.getMessage());
            captureScreenshot("checkout_error_" + bookingNumber);
            throw new RuntimeException("Checkout failed", e);
        }
    }
    
    private static void selectDurationIfAvailable() {
        try {
            List<WebElement> durations = driver.findElements(By.xpath("//h3[contains(text(), 'Month')]"));
            if (!durations.isEmpty()) {
                WebElement duration = durations.get(0);
                js.executeScript("arguments[0].scrollIntoView({block: 'center'});", duration);
                sleep(1);
                js.executeScript("arguments[0].click();", duration);
                log("✅ Selected duration");
                sleep(2);
            }
        } catch (Exception e) {
            log("ℹ️  No duration selection needed");
        }
    }
    
    private static void clickContinueIfPresent() {
        try {
            By[] continueSelectors = {
                By.xpath("//button[@type='button' and contains(@class, 'BtnNewCreate')]"),
                By.xpath("//button[contains(text(), 'Continue')]"),
                By.xpath("//button[contains(@class, 'ant-btn-primary') and contains(@class, 'ant-btn-block')]")
            };
            
            for (By selector : continueSelectors) {
                try {
                    WebElement continueBtn = driver.findElement(selector);
                    if (continueBtn.isDisplayed()) {
                        js.executeScript("arguments[0].scrollIntoView({block: 'center'});", continueBtn);
                        sleep(1);
                        js.executeScript("arguments[0].click();", continueBtn);
                        log("✅ Clicked Continue");
                        sleep(2);
                        return;
                    }
                } catch (Exception e) {
                }
            }
        } catch (Exception e) {
            log("ℹ️  No Continue button found");
        }
    }
    
    private static void clickPlaceOrderButton() {
        log("🛒 Clicking Place Order button...");
        
        try {
            WebElement placeOrderBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[contains(@class, 'btn-danger') and contains(@class, 'btn-block')]")));
            
            js.executeScript("arguments[0].scrollIntoView({block: 'center'});", placeOrderBtn);
            sleep(1);
            
            js.executeScript("arguments[0].click();", placeOrderBtn);
            log("✅ Clicked Place Order");
            sleep(3);
            
        } catch (Exception e) {
            log("❌ Place Order button click failed: " + e.getMessage());
            throw new RuntimeException("Place Order failed", e);
        }
    }
    
    private static void completePayment(int bookingNumber, String courseName) {
        log("💳 Completing payment process...");
        
        try {
            try {
                By[] paytmSelectors = {
                    By.xpath("//label[.//span[contains(text(), 'Paytm')]]"),
                    By.xpath("//span[contains(@class, 'ant-radio') and contains(text(), 'Paytm')]/parent::label")
                };
                
                for (By selector : paytmSelectors) {
                    try {
                        WebElement paytm = driver.findElement(selector);
                        if (paytm.isDisplayed()) {
                            js.executeScript("arguments[0].click();", paytm);
                            log("✅ Selected Paytm");
                            sleep(2);
                            break;
                        }
                    } catch (Exception e) {}
                }
            } catch (Exception e) {
                log("ℹ️  Paytm selection skipped");
            }
            
            By[] paymentBtnSelectors = {
                By.xpath("//button[@type='button' and contains(@class, 'ant-btn-primary') and contains(@class, 'ant-btn-block')]"),
                By.xpath("//button[contains(text(), 'Pay Now') or contains(text(), 'Place Order')]")
            };
            
            boolean paymentClicked = false;
            for (By selector : paymentBtnSelectors) {
                try {
                    WebElement payBtn = wait.until(ExpectedConditions.elementToBeClickable(selector));
                    js.executeScript("arguments[0].scrollIntoView({block: 'center'});", payBtn);
                    sleep(1);
                    js.executeScript("arguments[0].click();", payBtn);
                    log("✅ Clicked Pay Now button");
                    sleep(2);
                    paymentClicked = true;
                    break;
                } catch (Exception e) {}
            }
            
            if (!paymentClicked) {
                throw new RuntimeException("Could not click Pay Now button");
            }
            
            log("⏳ Waiting 30 seconds for QR code...");
            sleep(30);
            
            captureScreenshot("payment_" + bookingNumber + "_" + sanitizeFileName(courseName));
            log("✅ QR code screenshot captured");
            
            closePaymentWindow();
            
        } catch (Exception e) {
            log("❌ Payment completion failed: " + e.getMessage());
            throw new RuntimeException("Payment failed", e);
        }
    }
    
    private static void handleYesPopup() {
        try {
            By[] yesSelectors = {
                By.xpath("//button[@type='button']//span[contains(text(), 'Yes')]"),
                By.xpath("//button[contains(@class, 'ant-btn')]//span[text()='Yes']"),
                By.xpath("//span[text()='Yes']/parent::button")
            };
            
            for (By selector : yesSelectors) {
                try {
                    WebElement yesBtn = driver.findElement(selector);
                    if (yesBtn.isDisplayed()) {
                        js.executeScript("arguments[0].click();", yesBtn);
                        log("✅ Clicked Yes popup");
                        sleep(2);
                        return;
                    }
                } catch (Exception e) {}
            }
        } catch (Exception e) {}
    }
    
    private static void closePaymentWindow() {
        try {
            By[] closeSelectors = {
                By.xpath("//span[contains(@class, 'ptm-cross') and @id='app-close-btn']"),
                By.id("app-close-btn"),
                By.xpath("//span[contains(@class, 'ptm-cross')]")
            };
            
            for (By selector : closeSelectors) {
                try {
                    WebElement closeBtn = driver.findElement(selector);
                    js.executeScript("arguments[0].click();", closeBtn);
                    log("✅ Closed payment window");
                    sleep(8);
                    break;
                } catch (Exception e) {}
            }
            
            By[] skipSelectors = {
                By.xpath("//button[contains(@class, 'ptm-feedback-btn') and contains(text(), 'Skip')]"),
                By.xpath("//button[contains(text(), 'Skip')]")
            };
            
            for (By selector : skipSelectors) {
                try {
                    WebElement skipBtn = driver.findElement(selector);
                    js.executeScript("arguments[0].click();", skipBtn);
                    sleep(2);
                    break;
                } catch (Exception e) {}
            }
            
            By[] modalSelectors = {
                By.xpath("//span[contains(@class, 'ant-modal-close-x')]"),
                By.xpath("//button[contains(@class, 'ant-modal-close')]")
            };
            
            for (By selector : modalSelectors) {
                try {
                    WebElement modalBtn = driver.findElement(selector);
                    js.executeScript("arguments[0].click();", modalBtn);
                    sleep(2);
                    break;
                } catch (Exception e) {}
            }
            
        } catch (Exception e) {
            log("ℹ️  Payment window handling complete");
        }
    }
    
    private static void navigateBackToMentorDesk() {
        log("");
        log("🔙 Navigating back to Mentor Desk...");
        
        try {
            driver.get("https://www.damsdelhi.com/");
            sleep(3);
            log("✅ Loaded homepage");
            
            WebElement hamburgerBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.className("humburgerIcon")));
            js.executeScript("arguments[0].click();", hamburgerBtn);
            sleep(2);
            log("✅ Clicked hamburger menu");
            
            boolean clicked = clickMentorDeskElement();
            
            if (!clicked) {
                throw new RuntimeException("Could not navigate back to Mentor Desk");
            }
            
            sleep(5);
            log("✅ Back to Mentor Desk");
            
            handleYesPopup();
            
        } catch (Exception e) {
            log("⚠️  Navigation back warning: " + e.getMessage());
        }
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
            File destFile = new File(fullFileName);
            
            // Use Java NIO Files.copy instead of Apache Commons FileUtils
            try {
                Files.copy(screenshot.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                log("⚠️  Screenshot copy failed, trying alternative method...");
                // Fallback: manual copy
                java.io.FileInputStream fis = new java.io.FileInputStream(screenshot);
                java.io.FileOutputStream fos = new java.io.FileOutputStream(destFile);
                byte[] buffer = new byte[1024];
                int length;
                while ((length = fis.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
                fis.close();
                fos.close();
            }
            
            screenshots.add(fullFileName);
            log("📸 Screenshot: " + fullFileName);
            return fullFileName;
        } catch (Exception e) {
            log("❌ Screenshot failed: " + e.getMessage());
            return null;
        }
    }
    
    private static void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private static void generateReport() {
        log("");
        log("📊 Generating HTML report...");
        
        try {
            String timestamp = fileFormat.format(new Date());
            String reportFileName = "DAMS_Random_Booking_Report_" + timestamp + ".html";
            
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html>\n<html>\n<head>\n");
            html.append("<meta charset='UTF-8'>\n");
            html.append("<title>DAMS Random Booking Report</title>\n");
            html.append("<style>\n");
            html.append("body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }\n");
            html.append("h1 { color: #333; border-bottom: 3px solid #4CAF50; padding-bottom: 10px; }\n");
            html.append(".summary { background: white; padding: 20px; margin: 20px 0; border-radius: 5px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }\n");
            html.append(".summary h2 { color: #2196F3; margin-top: 0; }\n");
            html.append(".config { background: #e3f2fd; padding: 15px; margin: 10px 0; border-radius: 5px; border-left: 4px solid #2196F3; }\n");
            html.append(".success-box { background: #c8e6c9; padding: 15px; margin: 10px 0; border-radius: 5px; border-left: 4px solid #4CAF50; }\n");
            html.append(".log-container { background: white; padding: 20px; margin: 20px 0; border-radius: 5px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }\n");
            html.append(".log-entry { padding: 5px; margin: 2px 0; font-family: 'Courier New', monospace; font-size: 14px; }\n");
            html.append(".success { color: #4CAF50; }\n");
            html.append(".error { color: #f44336; }\n");
            html.append(".warning { color: #ff9800; }\n");
            html.append(".info { color: #2196F3; }\n");
            html.append(".screenshots { background: white; padding: 20px; margin: 20px 0; border-radius: 5px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }\n");
            html.append(".screenshot-item { margin: 20px 0; text-align: center; }\n");
            html.append(".screenshot-item h3 { color: #FF5722; margin-bottom: 10px; }\n");
            html.append("img { max-width: 800px; margin: 10px; border: 2px solid #ddd; border-radius: 5px; }\n");
            html.append("</style>\n</head>\n<body>\n");
            
            html.append("<h1>🚀 DAMS Random Booking Automation Report</h1>\n");
            
            html.append("<div class='summary'>\n");
            html.append("<h2>📋 Execution Summary</h2>\n");
            html.append("<div class='config'>\n");
            html.append("<p><strong>Phone Number:</strong> ").append(PHONE_NUMBER).append("</p>\n");
            html.append("<p><strong>Target Bookings:</strong> ").append(NUMBER_OF_BOOKINGS).append("</p>\n");
            html.append("<p><strong>Environment:</strong> ").append(System.getenv("CI") != null ? "GitHub Actions" : "Local").append("</p>\n");
            html.append("</div>\n");
            
            html.append("<div class='success-box'>\n");
            html.append("<p><strong>✅ Bookings Completed Successfully</strong></p>\n");
            html.append("<p><strong>Courses Booked:</strong> ").append(bookedCourseNames.size()).append("</p>\n");
            html.append("</div>\n");
            
            html.append("<p><strong>Execution Time:</strong> ").append(timestamp).append("</p>\n");
            html.append("<p><strong>Total Log Entries:</strong> ").append(logMessages.size()).append("</p>\n");
            html.append("<p><strong>Screenshots Captured:</strong> ").append(screenshots.size()).append("</p>\n");
            html.append("</div>\n");
            
            html.append("<div class='log-container'>\n");
            html.append("<h2>📝 Execution Log</h2>\n");
            for (String logEntry : logMessages) {
                String cssClass = "log-entry";
                if (logEntry.contains("✅")) cssClass += " success";
                else if (logEntry.contains("❌")) cssClass += " error";
                else if (logEntry.contains("⚠️")) cssClass += " warning";
                else cssClass += " info";
                
                html.append("<div class='").append(cssClass).append("'>")
                    .append(logEntry.replace("<", "&lt;").replace(">", "&gt;"))
                    .append("</div>\n");
            }
            html.append("</div>\n");
            
            if (!screenshots.isEmpty()) {
                html.append("<div class='screenshots'>\n");
                html.append("<h2>📸 Screenshots</h2>\n");
                for (String screenshot : screenshots) {
                    String fileName = new File(screenshot).getName();
                    html.append("<div class='screenshot-item'>\n");
                    html.append("<h3>").append(fileName).append("</h3>\n");
                    html.append("<img src='").append(screenshot).append("' alt='").append(fileName).append("'>\n");
                    html.append("</div>\n");
                }
                html.append("</div>\n");
            }
            
            html.append("</body>\n</html>");
            
            FileWriter writer = new FileWriter(reportFileName);
            writer.write(html.toString());
            writer.close();
            
            log("✅ Report generated: " + reportFileName);
            
        } catch (Exception e) {
            log("❌ Report generation failed: " + e.getMessage());
        }
    }
}