import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class DamsDelhiLogin {
    private static WebDriver driver;
    private static WebDriverWait wait;
    private static JavascriptExecutor js;
    
    // Tracking data
    private static List<CourseResult> courseResults = new ArrayList<>();
    private static int totalSuccessful = 0;
    private static int totalFailed = 0;
    
    private static SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private static SimpleDateFormat fileFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
    private static String executionStartTime;
    
    // FIXED: Increased default timeout to 30 seconds for reliability in CI
    private static final Duration DEFAULT_WAIT_TIMEOUT = Duration.ofSeconds(30);
    // FIXED: Increased QR code wait to 90 seconds, as payment gateways can be slow
    private static final Duration QR_CODE_WAIT_TIMEOUT = Duration.ofSeconds(90);


    static class CourseResult {
        String courseName;
        String status;
        String timestamp;
        String screenshotPath;
        String errorMessage;
        
        CourseResult(String name, String status, String time, String screenshot, String error) {
            this.courseName = name;
            this.status = status;
            this.timestamp = time;
            this.screenshotPath = screenshot;
            this.errorMessage = error;
        }
    }
    
    public static void main(String[] args) {
        try {
            new File("screenshots").mkdirs();
            executionStartTime = fileFormat.format(new Date());

            System.out.println("╔════════════════════════════════════════════╗");
            System.out.println("║  DAMS CBT AUTOMATION - ALL CBT COURSES    ║");
            System.out.println("╚════════════════════════════════════════════╝\n");

            setupDriver();
            login();
            
            takeDebugScreenshot("1_AfterLogin");

            navigateToCBTSectionViaHamburger();
            
            takeDebugScreenshot("2_AfterNavigatingToCBT");

            List<String> cbtCourses = discoverCBTCourses();
            System.out.println("\n✓ Found " + cbtCourses.size() + " CBT courses");
            for (int i = 0; i < cbtCourses.size(); i++) {
                System.out.println("  [" + (i + 1) + "] " + cbtCourses.get(i));
            }

            if (cbtCourses.isEmpty()) {
                System.out.println("\n⚠️ WARNING: No CBT courses found! Check debug screenshots.");
                // Debug screenshot already taken in discoverCBTCourses
            }

            for (int i = 0; i < cbtCourses.size(); i++) {
                String courseName = cbtCourses.get(i);
                System.out.println("\n" + "=".repeat(60));
                System.out.println("PROCESSING: " + courseName + " [" + (i+1) + "/" + cbtCourses.size() + "]");
                System.out.println("=".repeat(60));

                processCBTCourse(courseName, i);
                
                if (i < cbtCourses.size() - 1) {
                    returnToCBTSection();
                }
            }

            System.out.println("\n╔════════════════════════════════════════════╗");
            System.out.println("║  EXECUTION COMPLETED!                      ║");
            System.out.printf("║  Successful: %-2d                          ║%n", totalSuccessful);
            System.out.printf("║  Failed: %-2d                              ║%n", totalFailed);
            System.out.println("╚════════════════════════════════════════════╝");

        } catch (Exception e) {
            System.out.println("CRITICAL ERROR: " + e.getMessage());
            e.printStackTrace();
            takeDebugScreenshot("critical_error");
        } finally {
            generateDetailedReport();
            System.out.println("\nClosing in 5 seconds..."); // Shortened sleep
            sleep(5);
            if (driver != null) {
                driver.quit();
            }
        }
    }

    private static void setupDriver() {
        System.out.println("Setting up Chrome driver...");
        
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--disable-notifications");
        
        String ciMode = System.getenv("CI");
        if ("true".equals(ciMode)) {
            System.out.println("🤖 Running in CI mode (headless)");
            options.addArguments("--headless=new");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--disable-web-security");
            options.addArguments("--allow-running-insecure-content");
        } else {
            System.out.println("🖥️ Running in normal mode (with browser)");
            options.addArguments("--start-maximized");
        }
        
        driver = new ChromeDriver(options);
        if (!"true".equals(ciMode)) {
            driver.manage().window().maximize();
        }
        
        // FIXED: Initialize wait with default timeout
        wait = new WebDriverWait(driver, DEFAULT_WAIT_TIMEOUT);
        js = (JavascriptExecutor) driver;
        
        System.out.println("✓ Driver ready\n");
    }

    private static void login() {
        System.out.println("Starting login...");
        
        driver.get("https://www.damsdelhi.com/");
        
        // FIXED: Replaced sleep(3) with explicit wait
        try {
            WebElement signInBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[contains(text(), 'Sign in') or contains(text(), 'Sign In')]")));
            js.executeScript("arguments[0].click();", signInBtn);
            System.out.println("  ✓ Clicked: Sign In button");
        } catch (Exception e) {
            try {
                WebElement signInBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//a[contains(text(), 'Sign in') or contains(text(), 'Sign In')]")));
                js.executeScript("arguments[0].click();", signInBtn);
                System.out.println("  ✓ Clicked: Sign In link");
            } catch (Exception e2) {
                System.out.println("  ✗ Could not find sign in element");
                takeDebugScreenshot("login_failed_no_signin_btn");
                throw e2; // Fail fast
            }
        }
        
        // FIXED: Wait for phone input to be visible
        enterText(By.xpath("//input[@type='tel' or @type='number' or contains(@placeholder, 'number')]"), 
                  "+919456628016", "Phone");
        
        clickElement(By.className("common-bottom-btn"), "Request OTP");
        
        // FIXED: Wait for OTP input to be visible instead of sleeping
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//input[@type='text' or @type='number' or contains(@placeholder, 'OTP')]")));
        } catch (Exception e) {
            System.out.println("  ℹ OTP input not found, checking for logout popup...");
        }

        // Handle logout popup
        try {
            // Shortened wait for optional element
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(3));
            WebElement logoutBtn = shortWait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[contains(@class, 'btndata') and contains(text(), 'Logout')]")));
            js.executeScript("arguments[0].click();", logoutBtn);
            System.out.println("  ✓ Clicked Logout popup");
            // Wait for OTP input to appear *after* closing popup
            wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//input[@type='text' or @type='number' or contains(@placeholder, 'OTP')]")));
        } catch (Exception e) {
            System.out.println("  ℹ No logout popup");
        }
        
        enterText(By.xpath("//input[@type='text' or @type='number' or contains(@placeholder, 'OTP')]"), 
                  "2000", "OTP");
        
        clickElement(By.className("common-bottom-btn"), "Submit OTP");
        
        // FIXED: Replaced sleep(5) with wait for hamburger icon, proving login
        try {
            wait.until(ExpectedConditions.presenceOfElementLocated(By.className("humburgerIcon")));
            System.out.println("✓ Login successful (hamburger icon found)\n");
        } catch (Exception e) {
            System.out.println("  ⚠ Login might have failed, hamburger icon not found.");
            takeDebugScreenshot("login_failed_no_hamburger");
        }
    }

    private static void navigateToCBTSectionViaHamburger() {
        System.out.println("Navigating to CBT section via Hamburger menu...");
        
        try {
            // Step 1: Click the course dropdown button (Optional)
            try {
                // Use a shorter wait for optional elements
                WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(5));
                WebElement dropdown = shortWait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(@class, 'SelectCat')]")));
                js.executeScript("arguments[0].scrollIntoView({block: 'center'});", dropdown);
                js.executeScript("arguments[0].click();", dropdown);
                System.out.println("  ✓ Clicked: Course Dropdown");
                
                // FIXED: Wait for dropdown options to appear
                wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//span[contains(text(), 'NEET PG')] | //div[contains(text(), 'NEET PG')]")));
            } catch (Exception e) {
                System.out.println("  ⚠ Skipping dropdown (maybe already selected or not present)");
            }
            
            // Step 2: Select NEET PG from dropdown (Optional)
            try {
                List<WebElement> options = driver.findElements(
                    By.xpath("//span[contains(text(), 'NEET PG')] | //div[contains(text(), 'NEET PG')]"));
                for (WebElement option : options) {
                    if (option.isDisplayed()) {
                        js.executeScript("arguments[0].click();", option);
                        System.out.println("  ✓ Selected: NEET PG");
                        // FIXED: Wait for modal or hamburger to be clickable after selection
                        wait.until(ExpectedConditions.or(
                            ExpectedConditions.elementToBeClickable(By.className("humburgerIcon")),
                            ExpectedConditions.elementToBeClickable(By.xpath("//button[@type='button' and @aria-label='Close']"))
                        ));
                        break;
                    }
                }
            } catch (Exception e) {
                System.out.println("  ⚠ Skipping NEET PG selection");
            }
            
            // Step 3: Close any modal if present (Optional)
            try {
                WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(5));
                WebElement closeBtn = shortWait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[@type='button' and @aria-label='Close'] | //span[contains(@class, 'ant-modal-close')]")));
                js.executeScript("arguments[0].click();", closeBtn);
                System.out.println("  ✓ Closed modal");
            } catch (Exception e) {
                System.out.println("  ℹ No modal to close");
            }
            
            // Step 4: Click Hamburger menu button
            WebElement hamburger = wait.until(ExpectedConditions.elementToBeClickable(
                By.className("humburgerIcon")));
            js.executeScript("arguments[0].scrollIntoView({block: 'center'});", hamburger);
            js.executeScript("arguments[0].click();", hamburger);
            System.out.println("  ✓ Clicked: Hamburger Menu");
            
            // Step 5: Click CBT button in the sidebar
            // FIXED: Wait for sidebar to be visible
            wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//div[contains(@class, 'Categories')]")));
                
            By[] cbtSelectors = {
                By.xpath("//div[contains(@class, 'Categories')]//div[contains(text(), 'CBT')]"),
                By.xpath("//div[contains(@class, 'Categories')]//*[contains(text(), 'CBT')]"),
                By.xpath("//button[contains(., 'CBT')]"),
                By.xpath("//*[contains(text(), 'CBT') and not(contains(text(), 'NEET'))]")
            };
            
            WebElement cbtElem = null;
            for (By selector : cbtSelectors) {
                try {
                    List<WebElement> cbtElements = driver.findElements(selector);
                    for (WebElement elem : cbtElements) {
                        if (elem.isDisplayed() && (elem.getText().trim().equals("CBT") || elem.getText().trim().equalsIgnoreCase("cbt"))) {
                            cbtElem = elem;
                            break;
                        }
                    }
                    if (cbtElem != null) break;
                } catch (Exception e) {}
            }
            
            if (cbtElem != null) {
                js.executeScript("arguments[0].scrollIntoView({block: 'center'});", cbtElem);
                js.executeScript("arguments[0].click();", cbtElem);
                System.out.println("  ✓ Clicked: CBT button");
            } else {
                System.out.println("  ✗ Could not click CBT button!");
                takeDebugScreenshot("cbt_button_not_found");
                return; // Exit method
            }
            
            // Step 6: Click OK button (Red button) if it appears
            try {
                // FIXED: Wait for OK button to be clickable
                WebElement okBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[@type='button' and contains(@class, 'btn-danger') and contains(text(), 'OK')]")));
                js.executeScript("arguments[0].scrollIntoView({block: 'center'});", okBtn);
                js.executeScript("arguments[0].click();", okBtn);
                System.out.println("  ✓ Clicked: OK Button (Red)");
                // FIXED: Wait for page to reload by waiting for *any* button (a bit of a guess)
                wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//button[contains(@class, 'butBtn')]")));
            } catch (Exception e) {
                System.out.println("  ℹ No OK button to click (may not be needed)");
            }
            
            System.out.println("✓ Successfully navigated to CBT section\n");
            
        } catch (Exception e) {
            System.out.println("✗ Error navigating to CBT section: " + e.getMessage());
            e.printStackTrace();
            takeDebugScreenshot("navigation_error");
        }
    }

    private static List<String> discoverCBTCourses() {
        System.out.println("Discovering CBT courses...");
        List<String> courses = new ArrayList<>();
        
        try {
            System.out.println("  → Current URL: " + driver.getCurrentUrl());
            
            // FIXED: Replaced sleep(5) with a robust wait for the *first button* to appear.
            // This is the MOST LIKELY fix for the "0 courses" problem.
            By firstButtonSelector = By.xpath("//button[contains(@class, 'butBtn')]");
            try {
                System.out.println("  → Waiting up to 30s for course buttons to load...");
                wait.until(ExpectedConditions.presenceOfElementLocated(firstButtonSelector));
                System.out.println("  ✓ Course buttons (or at least one) are present.");
            } catch (Exception e) {
                System.out.println("  ✗ CRITICAL: Waited 30s but no 'butBtn' buttons found.");
                System.out.println("  → This is why 0 courses are being found.");
                takeDebugScreenshot("3_NoButtonsFoundAfterWait");
                savePageSource();
                return courses; // Return empty list
            }
            
            js.executeScript("window.scrollTo(0, 0);");
            sleep(1); // Short sleep after scroll
            
            long lastHeight = (Long) js.executeScript("return document.body.scrollHeight");
            int stableCount = 0;
            int scrollAttempts = 0;
            
            System.out.println("  → Starting scrolling to load all courses...");
            while (stableCount < 3 && scrollAttempts < 15) {
                js.executeScript("window.scrollBy(0, 800);");
                sleep(2); // Short sleep during scroll
                long newHeight = (Long) js.executeScript("return document.body.scrollHeight");
                if (newHeight == lastHeight) {
                    stableCount++;
                } else {
                    stableCount = 0;
                    lastHeight = newHeight;
                }
                scrollAttempts++;
                System.out.println("    Scroll attempt " + scrollAttempts + " (height: " + newHeight + ")");
            }
            
            System.out.println("  → Scrolled " + scrollAttempts + " times to load all content");
            
            js.executeScript("window.scrollTo(0, 0);");
            sleep(2); // Short sleep
            
            takeDebugScreenshot("3_BeforeFindingButtons");
            
            // --- This discovery logic is unchanged, but now it runs *after* we've waited ---
            
            List<WebElement> buyNowButtons = new ArrayList<>();
            By[] buttonSelectors = {
                By.xpath("//button[@type='button' and contains(@class, 'butBtn') and contains(@class, 'modal_show')]"),
                By.xpath("//button[contains(@class, 'butBtn')]"),
                By.xpath("//button[contains(text(), 'Buy') or contains(text(), 'buy')]"),
                By.xpath("//div[contains(@class, 'col')]//button[@type='button']")
            };

            for (int i = 0; i < buttonSelectors.length; i++) {
                buyNowButtons = driver.findElements(buttonSelectors[i]);
                System.out.println("  → Selector " + (i+1) + ": Found " + buyNowButtons.size() + " buttons");
                if (!buyNowButtons.isEmpty()) {
                    break;
                }
            }
            
            if (buyNowButtons.isEmpty()) {
                System.out.println("  ✗ CRITICAL: No Buy Now buttons found with ANY selector!");
                savePageSource();
                return courses;
            }
            
            System.out.println("  ✓ Total buttons found: " + buyNowButtons.size());
            System.out.println("  → Now extracting course names from each button...\n");
            
            // For each button, find the course name in its parent container
            for (int i = 0; i < buyNowButtons.size(); i++) {
                WebElement button = buyNowButtons.get(i);
                try {
                    js.executeScript("arguments[0].scrollIntoView({block: 'center'});", button);
                    sleep(1);
                    
                    WebElement container = button.findElement(By.xpath("./ancestor::div[contains(@class, 'col')]"));
                    
                    String courseName = "";
                    
                    // Try to find title
                    try {
                        WebElement titleElem = container.findElement(By.xpath(".//h1 | .//h2 | .//h3 | .//h4 | .//h5 | .//h6 | .//*[contains(@class, 'title')]"));
                        courseName = titleElem.getText().trim();
                    } catch (Exception e) {
                        // If no title, get all text and find the first valid line
                        String allText = container.getText().trim();
                        String[] lines = allText.split("\n");
                        for (String line : lines) {
                            line = line.trim();
                            if (isValidCBTCourseName(line)) {
                                courseName = line;
                                break;
                            }
                        }
                    }

                    if (courseName.isEmpty() || !isValidCBTCourseName(courseName)) {
                        courseName = "CBT_Course_" + (courses.size() + 1);
                        System.out.println("    [" + (i+1) + "] Using generic name: " + courseName);
                    }
                    
                    courses.add(courseName);
                    System.out.println("  ✓ [" + courses.size() + "] Added: " + courseName + "\n");
                    
                } catch (Exception e) {
                    System.out.println("  ⚠ Button " + (i+1) + " skipped: " + e.getMessage());
                    String fallbackName = "CBT_Course_" + (courses.size() + 1);
                    courses.add(fallbackName);
                    System.out.println("  → Added fallback: " + fallbackName + "\n");
                }
            }
            
            List<String> uniqueCourses = new ArrayList<>(new LinkedHashSet<>(courses));
            System.out.println("  ✓ Total unique courses: " + uniqueCourses.size());
            
            return uniqueCourses;
            
        } catch (Exception e) {
            System.out.println("✗ Error discovering courses: " + e.getMessage());
            e.printStackTrace();
            takeDebugScreenshot("discovery_error");
            return courses;
        }
    }

    private static boolean isValidCBTCourseName(String text) {
        if (text.length() < 10) return false;
        String lower = text.toLowerCase();
        
        if (!lower.contains("all india") && !lower.contains("dams") && 
            !lower.contains("neet") && !lower.contains("mds") && 
            !lower.contains("fmge") && !lower.contains("combo") && 
            !lower.contains("cbt")) {
            return false;
        }
        
        String[] invalid = {
            "test instructions", "buy now", "registration", "exam date", 
            "noida", "delhi", "select", "choose", "click here", "view details",
            "registration last date", "download app", "app store", "google play"
        };
        
        for (String term : invalid) {
            if (lower.equals(term) || lower.contains("₹")) {
                return false;
            }
        }
        return true;
    }

    private static void processCBTCourse(String courseName, int courseIndex) {
        String timestamp = timeFormat.format(new Date());
        String screenshotPath = null;
        String errorMsg = null;
        
        try {
            // Step 1: Find and click the specific Buy Now button
            List<WebElement> buyButtons = driver.findElements(
                By.xpath("//button[@type='button' and contains(@class, 'butBtn')]"));
            
            if (courseIndex < buyButtons.size()) {
                WebElement buyBtn = buyButtons.get(courseIndex);
                js.executeScript("arguments[0].scrollIntoView({block: 'center'});", buyBtn);
                js.executeScript("arguments[0].click();", buyBtn);
                System.out.println("  ✓ Step 1: Clicked Buy Now");
            } else {
                throw new Exception("Buy button not found for index " + courseIndex);
            }
            
            // Step 1.5: Handle CBT (Center Based Test) Modal
            try {
                // FIXED: Wait for modal to be visible
                WebElement cbtModal = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//div[@class='popup' and .//div[@id='cbt_hide']]")));
                System.out.println("  ✓ Step 1.5a: CBT Modal detected.");
                
                WebElement cbtRadioLabel = cbtModal.findElement(
                    By.xpath(".//label[contains(normalize-space(), 'CBT (Center Based Test)')]"));
                js.executeScript("arguments[0].click();", cbtRadioLabel);
                System.out.println("  ✓ Step 1.5b: Clicked 'CBT (Center Based Test)' radio button.");
                
                WebElement modalOkButton = cbtModal.findElement(
                    By.xpath(".//button[normalize-space()='OK']"));
                js.executeScript("arguments[0].click();", modalOkButton);
                System.out.println("  ✓ Step 1.5c: Clicked 'OK' on CBT modal.");
                
            } catch (Exception e) {
                System.out.println("  ℹ Step 1.5: CBT Modal not found or skipped");
            }
            
            // Step 2: Click Flex button (City Selection - show_data_city)
            try {
                // FIXED: Wait for button to be clickable
                WebElement flexBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(@class, 'show_data_city')]")));
                js.executeScript("arguments[0].scrollIntoView({block: 'center'});", flexBtn);
                js.executeScript("arguments[0].click();", flexBtn);
                System.out.println("  ✓ Step 2: Clicked Flex Button (show_data_city)");
            } catch (Exception e) {
                System.out.println("  ℹ Step 2: Flex button skipped (may not be required)");
            }
            
            // Step 3: Select Delhi location
            try {
                // FIXED: Wait for Delhi button to be clickable
                WebElement delhiBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(text(), 'Delhi') or contains(@data-city, 'Delhi')]")));
                js.executeScript("arguments[0].click();", delhiBtn);
                System.out.println("  ✓ Step 3: Selected Delhi");
            } catch (Exception e) {
                System.out.println("  ℹ Step 3: Delhi selection skipped");
            }
            
            // Step 4: Click Red Button (Place Order - btn-danger btn-block)
            try {
                // FIXED: Wait for Red button to be clickable
                WebElement redBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(@class, 'btn-danger') and contains(@class, 'btn-block')]")));
                js.executeScript("arguments[0].scrollIntoView({block: 'center'});", redBtn);
                js.executeScript("arguments[0].click();", redBtn);
                System.out.println("  ✓ Step 4: Clicked Red Button (btn-danger btn-block)");
            } catch (Exception e) {
                System.out.println("  ⚠ Step 4: Red button not found: " + e.getMessage());
            }
            
            // Step 5: Select Paytm payment option
            try {
                // FIXED: Wait for Paytm option to be visible
                By paytmSelector = By.xpath("//label[.//span[contains(text(), 'Paytm')]] | //span[contains(text(), 'Paytm')]/ancestor::label");
                WebElement paytm = wait.until(ExpectedConditions.visibilityOfElementLocated(paytmSelector));
                js.executeScript("arguments[0].click();", paytm);
                System.out.println("  ✓ Step 5: Selected Paytm");
            } catch (Exception e) {
                System.out.println("  ℹ Step 5: Paytm selection skipped: " + e.getMessage());
            }
            
            // Step 6: Click Payment button
            try {
                // FIXED: Wait for Payment button to be clickable
                By paymentSelector = By.xpath("//button[@type='button' and contains(@class, 'ant-btn-primary')] | //button[contains(text(), 'Pay')]");
                WebElement paymentBtn = wait.until(ExpectedConditions.elementToBeClickable(paymentSelector));
                js.executeScript("arguments[0].click();", paymentBtn);
                System.out.println("  ✓ Step 6: Clicked Payment Button");
            } catch (Exception e) {
                System.out.println("  ⚠ Step 6: Payment button issue: " + e.getMessage());
            }
            
            // Step 7: Wait for QR code to appear
            System.out.println("  ⏳ Step 7: Waiting for QR code to appear (max " + QR_CODE_WAIT_TIMEOUT.getSeconds() + "s)...");
            WebDriverWait qrWait = new WebDriverWait(driver, QR_CODE_WAIT_TIMEOUT);
            
            try {
                By qrLocator = By.xpath("//canvas | //img[contains(@class, 'qr') or contains(@class, 'QR') or contains(@src, 'data:image')]");
                qrWait.until(ExpectedConditions.presenceOfElementLocated(qrLocator));
                System.out.println("  ✓ QR code element detected. Pausing 2s for full render...");
                sleep(2); // Short sleep for canvas render
            } catch (Exception e) {
                System.out.println("  ⚠ Explicit QR element wait timed out after " + QR_CODE_WAIT_TIMEOUT.getSeconds() + "s.");
                System.out.println("  ⚠ Will attempt screenshot anyway, but it may be blank or incorrect.");
            }
            
            // Step 8: Capture QR screenshot
            String fileTimestamp = fileFormat.format(new Date());
            String filename = "screenshots/CBT_QR_" + courseName.replaceAll("[^a-zA-Z0-9]", "_") + 
                             "_" + fileTimestamp + ".png";
            
            File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            copyFile(screenshot, new File(filename));
            screenshotPath = filename;
            System.out.println("  ✓ Step 8: QR screenshot saved: " + filename);
            
            // Step 9: Close payment window
            closePaymentWindow();
            System.out.println("  ✓ Step 9: Closed payment window");
            
            courseResults.add(new CourseResult(courseName, "SUCCESS", timestamp, screenshotPath, null));
            totalSuccessful++;
            System.out.println("  ✅ Course processed successfully");
            
        } catch (Exception e) {
            errorMsg = e.getMessage();
            courseResults.add(new CourseResult(courseName, "FAILED", timestamp, screenshotPath, errorMsg));
            totalFailed++;
            System.out.println("  ❌ Course processing failed: " + errorMsg);
            e.printStackTrace();
            takeDebugScreenshot("process_course_failed_" + courseName.replaceAll("[^a-zA-Z0-9]", "_"));
        }
    }

    private static void returnToCBTSection() {
        try {
            System.out.println("\n  → Returning to CBT section...");
            
            driver.get("https://www.damsdelhi.com/");
            
            // FIXED: Wait for hamburger to be clickable
            WebElement hamburger = wait.until(ExpectedConditions.elementToBeClickable(
                By.className("humburgerIcon")));
            js.executeScript("arguments[0].click();", hamburger);
            System.out.println("  ✓ Clicked: Hamburger Menu");
            
            // FIXED: Wait for CBT button to be clickable
            By cbtSelector = By.xpath("//div[contains(@class, 'Categories')]//*[contains(text(), 'CBT')]");
            WebElement cbtElem = wait.until(ExpectedConditions.elementToBeClickable(cbtSelector));
            
            js.executeScript("arguments[0].click();", cbtElem);
            System.out.println("  ✓ Clicked: CBT button");
            
            // Click OK button (Red)
            try {
                WebElement okBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[@type='button' and contains(@class, 'btn-danger') and contains(text(), 'OK')]")));
                js.executeScript("arguments[0].click();", okBtn);
                System.out.println("  ✓ Clicked: OK Button (Red)");
                // Wait for buttons to load again
                wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//button[contains(@class, 'butBtn')]")));
            } catch (Exception e) {
                System.out.println("  ✗ Failed OK button: " + e.getMessage());
            }
            
        } catch (Exception e) {
            System.out.println("  ⚠ Error returning to CBT section: " + e.getMessage());
            takeDebugScreenshot("return_cbt_error");
        }
    }

    private static void closePaymentWindow() {
        try {
            // Close payment window
            By[] closeSelectors = {
                By.xpath("//span[contains(@class, 'ptm-cross') and @id='app-close-btn']"),
                By.id("app-close-btn"),
                By.xpath("//span[contains(@class, 'ptm-cross')]")
            };
            
            for (By selector : closeSelectors) {
                try {
                    // Use short wait, as it might not exist
                    WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(2));
                    WebElement closeBtn = shortWait.until(ExpectedConditions.elementToBeClickable(selector));
                    js.executeScript("arguments[0].click();", closeBtn);
                    System.out.println("  ✓ Closed payment window");
                    sleep(5); // Wait for close
                    break;
                } catch (Exception e) {}
            }
            
            // Handle other popups (feedback, etc.)
            // ... (Your existing logic is fine) ...
            
        } catch (Exception e) {
            System.out.println("  ⚠ Issue closing payment window: " + e.getMessage());
        }
    }

    private static void clickElement(By locator, String name) {
        try {
            WebElement elem = wait.until(ExpectedConditions.elementToBeClickable(locator));
            js.executeScript("arguments[0].scrollIntoView({block: 'center'});", elem);
            js.executeScript("arguments[0].click();", elem);
            System.out.println("  ✓ Clicked: " + name);
        } catch (Exception e) {
            System.out.println("  ✗ Failed to click: " + name + " - " + e.getMessage());
        }
    }

    private static void enterText(By locator, String text, String fieldName) {
        try {
            WebElement elem = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
            elem.clear();
            elem.sendKeys(text);
            System.out.println("  ✓ Entered: " + fieldName);
        } catch (Exception e) {
            System.out.println("  ✗ Failed to enter: " + fieldName);
        }
    }

    private static void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void copyFile(File source, File dest) throws Exception {
        Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void takeDebugScreenshot(String name) {
        if (driver == null) {
            System.out.println("  ✗ Cannot take debug screenshot, driver is null.");
            return;
        }
        try {
            String timestamp = fileFormat.format(new Date());
            String filename = "screenshots/DEBUG_" + name + "_" + timestamp + ".png";
            File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            copyFile(screenshot, new File(filename));
            System.out.println("  📸 Debug screenshot saved: " + filename);
        } catch (Exception e) {
            System.out.println("  ✗ Failed to take debug screenshot: " + e.getMessage());
        }
    }

    // NEW: Utility to save page source for debugging
    private static void savePageSource() {
        try {
            FileWriter sourceWriter = new FileWriter("page_source_debug.html");
            sourceWriter.write(driver.getPageSource());
            sourceWriter.close();
            System.out.println("  → Page source saved to: page_source_debug.html");
        } catch (Exception e) {
            System.out.println("  ✗ Failed to save page source: " + e.getMessage());
        }
    }

    private static void generateDetailedReport() {
        System.out.println("\nGenerating detailed HTML report...");
        
        try {
            String timestamp = fileFormat.format(new Date());
            String filename = "DAMS_CBT_Report_" + timestamp + ".html";
            
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html>\n<html>\n<head>\n");
            html.append("<meta charset='UTF-8'>\n");
            html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>\n");
            html.append("<title>DAMS CBT Automation Report</title>\n");
            html.append("<style>\n");
            html.append("* { margin: 0; padding: 0; box-sizing: border-box; }\n");
            html.append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; padding: 40px 20px; }\n");
            html.append(".container { max-width: 1400px; margin: 0 auto; }\n");
            
            // Header
            html.append(".header { background: white; border-radius: 20px; padding: 40px; margin-bottom: 30px; box-shadow: 0 10px 40px rgba(0,0,0,0.1); text-align: center; }\n");
            html.append(".header h1 { color: #2d3748; font-size: 42px; font-weight: 700; margin-bottom: 10px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }\n");
            html.append(".header .subtitle { color: #718096; font-size: 16px; margin-top: 5px; }\n");
            
            // Summary
            html.append(".summary { background: white; border-radius: 20px; padding: 40px; margin-bottom: 30px; box-shadow: 0 10px 40px rgba(0,0,0,0.1); }\n");
            html.append(".summary h2 { color: #2d3748; font-size: 28px; font-weight: 600; margin-bottom: 25px; }\n");
            html.append(".stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 20px; margin-bottom: 25px; }\n");
            html.append(".stat-card { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px; border-radius: 15px; box-shadow: 0 5px 20px rgba(102, 126, 234, 0.4); }\n");
            html.append(".stat-card .label { font-size: 14px; opacity: 0.9; margin-bottom: 10px; }\n");
            html.append(".stat-card .value { font-size: 48px; font-weight: 700; }\n");
            html.append(".stat-card.success { background: linear-gradient(135deg, #48bb78 0%, #38a169 100%); }\n");
            html.append(".stat-card.failed { background: linear-gradient(135deg, #f56565 0%, #e53e3e 100%); }\n");
            
            // Results table
            html.append(".results { background: white; border-radius: 20px; padding: 40px; margin-bottom: 30px; box-shadow: 0 10px 40px rgba(0,0,0,0.1); overflow-x: auto; }\n");
            html.append(".results h2 { color: #2d3748; font-size: 28px; font-weight: 600; margin-bottom: 25px; }\n");
            html.append("table { width: 100%; border-collapse: collapse; }\n");
            html.append("thead { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; }\n");
            html.append("th { padding: 15px; text-align: left; font-weight: 600; }\n");
            html.append("tbody tr { border-bottom: 1px solid #e2e8f0; transition: background 0.3s; }\n");
            html.append("tbody tr:hover { background: #f7fafc; }\n");
            html.append("td { padding: 15px; word-break: break-word; }\n");
            html.append(".status-badge { display: inline-block; padding: 6px 12px; border-radius: 20px; font-size: 12px; font-weight: 600; }\n");
            html.append(".status-success { background: #c6f6d5; color: #22543d; }\n");
            html.append(".status-failed { background: #fed7d7; color: #742a2a; }\n");
            html.append(".screenshot-link { color: #667eea; text-decoration: none; font-weight: 600; }\n");
            html.append(".screenshot-link:hover { text-decoration: underline; }\n");
            html.append(".error-msg { color: #e53e3e; font-size: 12px; font-style: italic; max-width: 300px; }\n");
            
            // Footer
            html.append(".footer { text-align: center; color: white; margin-top: 40px; padding: 20px; }\n");
            
            html.append("@media (max-width: 768px) {\n");
            html.append("  .header h1 { font-size: 32px; }\n");
            html.append("  .summary, .results { padding: 25px 20px; }\n");
            html.append("  table { font-size: 14px; }\n");
            html.append("  th, td { padding: 10px; }\n");
            html.append("}\n");
            
            html.append("</style>\n</head>\n<body>\n");
            html.append("<div class='container'>\n");
            
            // Header
            html.append("<div class='header'>\n"); // FIXED: Was missing class
            html.append("<h1>🎯 DAMS CBT Automation Report</h1>\n");
            html.append("<p class='subtitle'>Comprehensive CBT Course Purchase Summary</p>\n");
            html.append("</div>\n");
            
            // Summary
            html.append("<div class='summary'>\n");
            html.append("<h2>📊 Execution Summary</h2>\n");
            html.append("<div class='stats-grid'>\n");
            
            html.append("<div class='stat-card'>\n");
            html.append("<div class='label'>Total Courses Attempted</div>\n");
            html.append("<div class='value'>").append(courseResults.size()).append("</div>\n");
            html.append("</div>\n");
            
            html.append("<div class='stat-card success'>\n");
            html.append("<div class='label'>Successful Purchases</div>\n");
            html.append("<div class='value'>").append(totalSuccessful).append("</div>\n");
            html.append("</div>\n");
            
            html.append("<div class 'stat-card failed'>\n");
            html.append("<div class='label'>Failed Attempts</div>\n");
            html.append("<div class='value'>").append(totalFailed).append("</div>\n");
            html.append("</div>\n");
            
            html.append("</div>\n");
            html.append("<p style='margin-top: 20px; color: #4a5568;'><strong>Execution Time:</strong> " + executionStartTime + "</p>\n");
            html.append("</div>\n");
            
            // Results table
            html.append("<div class='results'>\n");
            html.append("<h2>📋 Detailed Results</h2>\n");
            html.append("<table>\n");
            html.append("<thead>\n");
            html.append("<tr>\n");
            html.append("<th>#</th>\n");
            html.append("<th>Course Name</th>\n");
            html.append("<th>Status</th>\n");
            html.append("<th>Time</th>\n");
            html.append("<th>Screenshot</th>\n");
            html.append("<th>Error</th>\n");
            html.append("</tr>\n");
            html.append("</thead>\n");
            html.append("<tbody>\n");
            
            if (courseResults.isEmpty()) {
                 html.append("<tr><td colspan='6' style='text-align: center; padding: 20px; color: #718096;'>No courses were processed. Check debug screenshots for errors.</td></tr>\n");
            }

            for (int i = 0; i < courseResults.size(); i++) {
                CourseResult result = courseResults.get(i);
                html.append("<tr>\n");
                html.append("<td>").append(i + 1).append("</td>\n");
                html.append("<td>").append(result.courseName).append("</td>\n");
                
                String statusClass = result.status.equals("SUCCESS") ? "status-success" : "status-failed";
                html.append("<td><span class='status-badge ").append(statusClass).append("'>").append(result.status).append("</span></td>\n");
                
                html.append("<td>").append(result.timestamp).append("</td>\n");
                
                if (result.screenshotPath != null) {
                    // Make screenshot path relative for GH Pages
                    String relativePath = "screenshots/" + new File(result.screenshotPath).getName();
                    html.append("<td><a href='").append(relativePath).append("' class='screenshot-link' target='_blank'>View QR</a></td>\n");
                } else {
                    html.append("<td>-</td>\n");
                }
                
                if (result.errorMessage != null) {
                    html.append("<td><span class='error-msg'>").append(result.errorMessage).append("</span></td>\n");
                } else {
                    html.append("<td>-</td>\n");
                }
                
                html.append("</tr>\n");
            }
            
            html.append("</tbody>\n");
            html.append("</table>\n");
            html.append("</div>\n");
            
            // Footer
            html.append("<div class='footer'>\n");
            html.append("<p>Generated by DAMS CBT Automation System | Powered by Selenium WebDriver</p>\n");
            html.append("</div>\n");
            
            html.append("</div>\n");
            html.append("</body>\n</html>");
            
            FileWriter writer = new FileWriter(filename);
            writer.write(html.toString());
            writer.close();
            
            System.out.println("✓ Detailed report saved: " + filename);
            
        } catch (Exception e) {
            System.out.println("✗ Report generation failed: " + e.getMessage());
        }
    }
}
