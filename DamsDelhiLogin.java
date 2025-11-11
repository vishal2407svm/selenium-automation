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

            // Navigate to CBT section using hamburger menu
            navigateToCBTSectionViaHamburger();

            // Discover all CBT courses
            List<String> cbtCourses = discoverCBTCourses();
            System.out.println("\n✓ Found " + cbtCourses.size() + " CBT courses");
            for (int i = 0; i < cbtCourses.size(); i++) {
                System.out.println("  [" + (i + 1) + "] " + cbtCourses.get(i));
            }

            // Process each CBT course
            for (int i = 0; i < cbtCourses.size(); i++) {
                String courseName = cbtCourses.get(i);
                System.out.println("\n" + "=".repeat(60));
                System.out.println("PROCESSING: " + courseName + " [" + (i+1) + "/" + cbtCourses.size() + "]");
                System.out.println("=".repeat(60));

                processCBTCourse(courseName, i);
                
                // Return to CBT section after each course (except last)
                if (i < cbtCourses.size() - 1) {
                    returnToCBTSection();
                }
            }

            System.out.println("\n╔════════════════════════════════════════════╗");
            System.out.println("║  EXECUTION COMPLETED!                      ║");
            System.out.println("║  Successful: " + totalSuccessful + "                              ║");
            System.out.println("║  Failed: " + totalFailed + "                                  ║");
            System.out.println("╚════════════════════════════════════════════╝");

        } catch (Exception e) {
            System.out.println("CRITICAL ERROR: " + e.getMessage());
            e.printStackTrace();
        } finally {
            generateDetailedReport();
            System.out.println("\nClosing in 10 seconds...");
            sleep(10);
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
        
        // ✅ CI/CD specific options - GitHub Actions ke liye
        String ciMode = System.getenv("CI");
        if ("true".equals(ciMode)) {
            System.out.println("🤖 Running in CI mode (headless)");
            options.addArguments("--headless=new");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
        } else {
            System.out.println("🖥️ Running in normal mode (with browser)");
            options.addArguments("--start-maximized");
        }
        
        driver = new ChromeDriver(options);
        if (!"true".equals(ciMode)) {
            driver.manage().window().maximize();
        }
        wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        js = (JavascriptExecutor) driver;
        
        System.out.println("✓ Driver ready\n");
    }

    private static void login() {
        System.out.println("Starting login...");
        
        driver.get("https://www.damsdelhi.com/");
        sleep(3);
        
        // Click Sign in button
        try {
            WebElement signInBtn = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//button[contains(text(), 'Sign in') or contains(text(), 'Sign In')]")));
            js.executeScript("arguments[0].click();", signInBtn);
            System.out.println("  ✓ Clicked: Sign In button");
            sleep(3);
        } catch (Exception e) {
            try {
                WebElement signInBtn = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//a[contains(text(), 'Sign in') or contains(text(), 'Sign In')]")));
                js.executeScript("arguments[0].click();", signInBtn);
                System.out.println("  ✓ Clicked: Sign In link");
                sleep(3);
            } catch (Exception e2) {
                System.out.println("  ✗ Could not find sign in element");
            }
        }
        
        enterText(By.xpath("//input[@type='tel' or @type='number' or contains(@placeholder, 'number')]"), 
                  "+919456628016", "Phone");
        sleep(2);
        
        clickElement(By.className("common-bottom-btn"), "Request OTP");
        sleep(3);
        
        // Handle logout popup
        try {
            WebElement logoutBtn = driver.findElement(
                By.xpath("//button[contains(@class, 'btndata') and contains(text(), 'Logout')]"));
            js.executeScript("arguments[0].click();", logoutBtn);
            System.out.println("  ✓ Clicked Logout popup");
            sleep(3);
        } catch (Exception e) {
            System.out.println("  ℹ No logout popup");
        }
        
        enterText(By.xpath("//input[@type='text' or @type='number' or contains(@placeholder, 'OTP')]"), 
                  "2000", "OTP");
        sleep(2);
        
        clickElement(By.className("common-bottom-btn"), "Submit OTP");
        sleep(5);
        
        System.out.println("✓ Login successful\n");
    }

    private static void navigateToCBTSectionViaHamburger() {
        System.out.println("Navigating to CBT section via Hamburger menu...");
        
        try {
            // Step 1: Click the course dropdown button to select NEET PG
            try {
                WebElement dropdown = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//button[contains(@class, 'SelectCat')]")));
                js.executeScript("arguments[0].scrollIntoView({block: 'center'});", dropdown);
                sleep(1);
                js.executeScript("arguments[0].click();", dropdown);
                System.out.println("  ✓ Clicked: Course Dropdown");
                sleep(3);
            } catch (Exception e) {
                System.out.println("  ⚠ Skipping dropdown: " + e.getMessage());
            }
            
            // Step 2: Select NEET PG from dropdown
            try {
                List<WebElement> options = driver.findElements(
                    By.xpath("//span[contains(text(), 'NEET PG')] | //div[contains(text(), 'NEET PG')]"));
                for (WebElement option : options) {
                    if (option.isDisplayed()) {
                        js.executeScript("arguments[0].click();", option);
                        System.out.println("  ✓ Selected: NEET PG");
                        sleep(3);
                        break;
                    }
                }
            } catch (Exception e) {
                System.out.println("  ⚠ Skipping NEET PG selection");
            }
            
            // Step 3: Close any modal if present
            try {
                WebElement closeBtn = driver.findElement(
                    By.xpath("//button[@type='button' and @aria-label='Close'] | //span[contains(@class, 'ant-modal-close')]"));
                js.executeScript("arguments[0].click();", closeBtn);
                System.out.println("  ✓ Closed modal");
                sleep(2);
            } catch (Exception e) {
                System.out.println("  ℹ No modal to close");
            }
            
            // Step 4: Click Hamburger menu button
            boolean hamburgerClicked = false;
            try {
                WebElement hamburger = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.className("humburgerIcon")));
                js.executeScript("arguments[0].scrollIntoView({block: 'center'});", hamburger);
                sleep(1);
                js.executeScript("arguments[0].click();", hamburger);
                System.out.println("  ✓ Clicked: Hamburger Menu");
                hamburgerClicked = true;
                sleep(3);
            } catch (Exception e) {
                System.out.println("  ✗ Failed to click hamburger: " + e.getMessage());
            }
            
            if (!hamburgerClicked) {
                System.out.println("  ✗ Could not open hamburger menu!");
                return;
            }
            
            // Step 5: Click CBT button in the sidebar
            boolean cbtClicked = false;
            
            By[] cbtSelectors = {
                By.xpath("//div[contains(@class, 'Categories')]//div[contains(text(), 'CBT')]"),
                By.xpath("//div[contains(@class, 'Categories')]//*[contains(text(), 'CBT')]"),
                By.xpath("//button[contains(., 'CBT')]"),
                By.xpath("//*[@role='button' and contains(., 'CBT')]"),
                By.xpath("//*[contains(text(), 'CBT') and not(contains(text(), 'NEET'))]")
            };
            
            for (By selector : cbtSelectors) {
                try {
                    List<WebElement> cbtElements = driver.findElements(selector);
                    for (WebElement cbtElem : cbtElements) {
                        if (cbtElem.isDisplayed()) {
                            String elemText = cbtElem.getText().trim();
                            System.out.println("    Found element with text: " + elemText);
                            
                            if (elemText.equals("CBT") || elemText.equalsIgnoreCase("cbt")) {
                                js.executeScript("arguments[0].scrollIntoView({block: 'center'});", cbtElem);
                                sleep(1);
                                js.executeScript("arguments[0].click();", cbtElem);
                                System.out.println("  ✓ Clicked: CBT button");
                                cbtClicked = true;
                                sleep(3);
                                break;
                            }
                        }
                    }
                    if (cbtClicked) break;
                } catch (Exception e) {
                    System.out.println("    Trying next selector...");
                }
            }
            
            if (!cbtClicked) {
                System.out.println("  ✗ Could not click CBT button!");
                System.out.println("  ℹ Trying to find and click any element with 'CBT' in sidebar...");
                
                try {
                    List<WebElement> allElements = driver.findElements(By.xpath("//*[contains(text(), 'CBT')]"));
                    System.out.println("  → Found " + allElements.size() + " elements containing 'CBT'");
                    
                    for (WebElement elem : allElements) {
                        try {
                            if (elem.isDisplayed()) {
                                String text = elem.getText().trim();
                                System.out.println("    Element: " + elem.getTagName() + " | Text: " + text);
                                
                                if (text.equals("CBT")) {
                                    js.executeScript("arguments[0].click();", elem);
                                    System.out.println("  ✓ Clicked: CBT (fallback method)");
                                    cbtClicked = true;
                                    sleep(3);
                                    break;
                                }
                            }
                        } catch (Exception e) {}
                    }
                } catch (Exception e) {
                    System.out.println("  ✗ Fallback also failed: " + e.getMessage());
                }
            }
            
            if (!cbtClicked) {
                System.out.println("  ✗ All attempts to click CBT failed!");
                return;
            }
            
            // Step 6: Click OK button (Red button) if it appears
            try {
                WebElement okBtn = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//button[@type='button' and contains(@class, 'btn-danger') and contains(text(), 'OK')]")));
                js.executeScript("arguments[0].scrollIntoView({block: 'center'});", okBtn);
                sleep(1);
                js.executeScript("arguments[0].click();", okBtn);
                System.out.println("  ✓ Step 6: Clicked: OK Button (Red)");
                sleep(3);
            } catch (Exception e) {
                System.out.println("  ℹ No OK button to click (may not be needed)");
            }
            
            System.out.println("✓ Successfully navigated to CBT section\n");
            
        } catch (Exception e) {
            System.out.println("✗ Error navigating to CBT section: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<String> discoverCBTCourses() {
        System.out.println("Discovering CBT courses...");
        List<String> courses = new ArrayList<>();
        
        try {
            // Scroll to load all courses
            js.executeScript("window.scrollTo(0, 0);");
            sleep(2);
            
            long lastHeight = (Long) js.executeScript("return document.body.scrollHeight");
            int stableCount = 0;
            
            while (stableCount < 3) {
                js.executeScript("window.scrollBy(0, 500);");
                sleep(1);
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
            
            // Find all Buy Now buttons
            List<WebElement> buyNowButtons = driver.findElements(
                By.xpath("//button[@type='button' and contains(@class, 'butBtn') and contains(@class, 'modal_show')]"));
            
            System.out.println("  Found " + buyNowButtons.size() + " Buy Now buttons");
            
            if (buyNowButtons.isEmpty()) {
                System.out.println("  ✗ No Buy Now buttons found!");
                System.out.println("  → Trying alternate selector...");
                
                buyNowButtons = driver.findElements(
                    By.xpath("//button[contains(@class, 'butBtn') and contains(text(), 'Buy Now')]"));
                System.out.println("  Found " + buyNowButtons.size() + " buttons with alternate selector");
            }
            
            if (buyNowButtons.isEmpty()) {
                return courses;
            }
            
            // For each button, find the course name in its parent container
            for (WebElement button : buyNowButtons) {
                try {
                    WebElement container = button.findElement(By.xpath("./ancestor::div[contains(@class, 'col')]"));
                    
                    String courseName = "";
                    
                    // Method 1: Look for heading tags
                    try {
                        WebElement titleElem = container.findElement(
                            By.xpath(".//h3 | .//h4 | .//h5 | .//*[contains(@class, 'title')]"));
                        courseName = titleElem.getText().trim();
                    } catch (Exception e) {}
                    
                    // Method 2: Look for anchor with substantial text
                    if (courseName.isEmpty()) {
                        try {
                            WebElement linkElem = container.findElement(
                                By.xpath(".//a[string-length(normalize-space(text())) > 15]"));
                            courseName = linkElem.getText().trim();
                        } catch (Exception e) {}
                    }
                    
                    // Method 3: Find any substantial text before the button
                    if (courseName.isEmpty()) {
                        List<WebElement> textElements = container.findElements(
                            By.xpath(".//*[string-length(normalize-space(text())) > 15]"));
                        for (WebElement elem : textElements) {
                            String text = elem.getText().trim();
                            if (isValidCBTCourseName(text)) {
                                courseName = text;
                                break;
                            }
                        }
                    }
                    
                    if (!courseName.isEmpty() && isValidCBTCourseName(courseName)) {
                        courses.add(courseName);
                        System.out.println("  → Found course: " + courseName);
                    } else {
                        courseName = "CBT Course " + (courses.size() + 1);
                        courses.add(courseName);
                        System.out.println("  → Found course: " + courseName + " (generic name)");
                    }
                } catch (Exception e) {
                    System.out.println("  ⚠ Skipped one card: " + e.getMessage());
                }
            }
            
            // Remove duplicates while preserving order
            List<String> uniqueCourses = new ArrayList<>(new LinkedHashSet<>(courses));
            
            return uniqueCourses;
            
        } catch (Exception e) {
            System.out.println("✗ Error discovering courses: " + e.getMessage());
            e.printStackTrace();
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
                By.xpath("//button[contains(@class, 'butBtn') and contains(@class, 'modal_show')]"));
            
            if (courseIndex < buyButtons.size()) {
                WebElement buyBtn = buyButtons.get(courseIndex);
                js.executeScript("arguments[0].scrollIntoView({block: 'center'});", buyBtn);
                sleep(2);
                js.executeScript("arguments[0].click();", buyBtn);
                System.out.println("  ✓ Step 1: Clicked Buy Now");
                sleep(3);
            } else {
                throw new Exception("Buy button not found for index " + courseIndex);
            }
            
            // Step 1.5: Handle CBT (Center Based Test) Modal
            try {
                WebElement cbtModal = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//div[@class='popup' and .//div[@id='cbt_hide']]")));
                System.out.println("  ✓ Step 1.5a: CBT Modal detected");
                
                WebElement cbtRadioLabel = cbtModal.findElement(
                    By.xpath(".//label[contains(normalize-space(), 'CBT (Center Based Test)')]"));
                js.executeScript("arguments[0].click();", cbtRadioLabel);
                System.out.println("  ✓ Step 1.5b: Selected CBT option");
                sleep(1);
                
                WebElement modalOkButton = cbtModal.findElement(
                    By.xpath(".//button[normalize-space()='OK']"));
                js.executeScript("arguments[0].click();", modalOkButton);
                System.out.println("  ✓ Step 1.5c: Clicked OK on modal");
                sleep(3);
                
            } catch (Exception e) {
                System.out.println("  ℹ Step 1.5: CBT Modal skipped");
            }
            
            // Step 2: Click Flex button
            try {
                WebElement flexBtn = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//button[contains(@class, 'show_data_city')]")));
                js.executeScript("arguments[0].scrollIntoView({block: 'center'});", flexBtn);
                sleep(1);
                js.executeScript("arguments[0].click();", flexBtn);
                System.out.println("  ✓ Step 2: Clicked Flex Button");
                sleep(2);
            } catch (Exception e) {
                System.out.println("  ℹ Step 2: Flex button skipped");
            }
            
            // Step 3: Select Delhi
            try {
                WebElement delhiBtn = driver.findElement(
                    By.xpath("//button[contains(text(), 'Delhi') or contains(@data-city, 'Delhi')]"));
                js.executeScript("arguments[0].click();", delhiBtn);
                System.out.println("  ✓ Step 3: Selected Delhi");
                sleep(2);
            } catch (Exception e) {
                System.out.println("  ℹ Step 3: Delhi selection skipped");
            }
            
            // Step 4: Click Red Button
            try {
                WebElement redBtn = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//button[contains(@class, 'btn-danger') and contains(@class, 'btn-block')]")));
                js.executeScript("arguments[0].scrollIntoView({block: 'center'});", redBtn);
                sleep(1);
                js.executeScript("arguments[0].click();", redBtn);
                System.out.println("  ✓ Step 4: Clicked Red Button");
                sleep(3);
            } catch (Exception e) {
                System.out.println("  ⚠ Step 4: Red button not found");
            }
            
            // Step 5: Select Paytm
            try {
                WebElement paytm = null;
                By[] paytmSelectors = {
                    By.xpath("//label[.//span[contains(text(), 'Paytm')]]"),
                    By.xpath("//span[contains(text(), 'Paytm')]/ancestor::label"),
                    By.xpath("//input[@value='paytm']/parent::label"),
                    By.xpath("//*[contains(text(), 'Paytm')]")
                };
                
                for (By selector : paytmSelectors) {
                    try {
                        paytm = wait.until(ExpectedConditions.presenceOfElementLocated(selector));
                        if (paytm.isDisplayed()) break;
                    } catch (Exception e) {}
                }
                
                if (paytm != null) {
                    js.executeScript("arguments[0].click();", paytm);
                    System.out.println("  ✓ Step 5: Selected Paytm");
                    sleep(2);
                }
            } catch (Exception e) {
                System.out.println("  ℹ Step 5: Paytm skipped");
            }
            
            // Step 6: Click Payment button
            try {
                WebElement paymentBtn = null;
                By[] paymentSelectors = {
                    By.xpath("//button[@type='button' and contains(@class, 'ant-btn-primary') and contains(@class, 'ant-btn-block')]"),
                    By.xpath("//button[contains(text(), 'Pay') or contains(text(), 'Proceed')]"),
                    By.xpath("//button[contains(@class, 'btn-primary') and contains(@class, 'btn-block')]")
                };
                
                for (By selector : paymentSelectors) {
                    try {
                        paymentBtn = wait.until(ExpectedConditions.presenceOfElementLocated(selector));
                        if (paymentBtn.isDisplayed()) break;
                    } catch (Exception e) {}
                }
                
                if (paymentBtn != null) {
                    js.executeScript("arguments[0].click();", paymentBtn);
                    System.out.println("  ✓ Step 6: Clicked Payment Button");
                    sleep(2);
                }
            } catch (Exception e) {
                System.out.println("  ⚠ Step 6: Payment button issue");
            }
            
            // Step 7: Wait for QR code
            System.out.println("  ⏳ Step 7: Waiting for QR code (max 60s)...");
            WebDriverWait qrWait = new WebDriverWait(driver, Duration.ofSeconds(60));
            
            try {
                By qrLocator = By.xpath("//canvas | //img[contains(@class, 'qr') or contains(@class, 'QR') or contains(@src, 'data:image')]");
                qrWait.until(ExpectedConditions.presenceOfElementLocated(qrLocator));
                System.out.println("  ✓ QR code detected");
                sleep(2);
            } catch (Exception e) {
                System.out.println("  ⚠ QR wait timeout, attempting screenshot anyway");
            }
            
            // Step 8: Capture screenshot
            String fileTimestamp = fileFormat.format(new Date());
            String filename = "screenshots/CBT_QR_" + courseName.replaceAll("[^a-zA-Z0-9]", "_") + 
                            "_" + fileTimestamp + ".png";
            
            File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            copyFile(screenshot, new File(filename));
            screenshotPath = filename;
            System.out.println("  ✓ Step 8: Screenshot saved: " + filename);
            
            // Step 9: Close payment window
            closePaymentWindow();
            System.out.println("  ✓ Step 9: Closed payment window");
            
            // Record success
            courseResults.add(new CourseResult(courseName, "SUCCESS", timestamp, screenshotPath, null));
            totalSuccessful++;
            System.out.println("  ✅ Course processed successfully");
            
        } catch (Exception e) {
            errorMsg = e.getMessage();
            courseResults.add(new CourseResult(courseName, "FAILED", timestamp, screenshotPath, errorMsg));
            totalFailed++;
            System.out.println("  ❌ Course processing failed: " + errorMsg);
            e.printStackTrace();
        }
    }

    private static void returnToCBTSection() {
        try {
            System.out.println("\n  → Returning to CBT section...");
            
            driver.get("https://www.damsdelhi.com/");
            sleep(3);
            
            boolean hamburgerClicked = false;
            try {
                WebElement hamburger = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.className("humburgerIcon")));
                js.executeScript("arguments[0].click();", hamburger);
                System.out.println("  ✓ Clicked: Hamburger Menu");
                hamburgerClicked = true;
                sleep(2);
            } catch (Exception e) {
                System.out.println("  ✗ Failed hamburger");
            }
            
            if (!hamburgerClicked) return;
            
            boolean cbtClicked = false;
            By[] cbtSelectors = {
                By.xpath("//div[contains(@class, 'Categories')]//div[contains(text(), 'CBT')]"),
                By.xpath("//div[contains(@class, 'Categories')]//*[contains(text(), 'CBT')]"),
                By.xpath("//*[contains(text(), 'CBT') and not(contains(text(), 'NEET'))]")
            };
            
            for (By selector : cbtSelectors) {
                try {
                    List<WebElement> cbtElements = driver.findElements(selector);
                    for (WebElement cbtElem : cbtElements) {
                        if (cbtElem.isDisplayed() && cbtElem.getText().trim().equals("CBT")) {
                            js.executeScript("arguments[0].click();", cbtElem);
                            System.out.println("  ✓ Clicked: CBT button");
                            cbtClicked = true;
                            sleep(2);
                            break;
                        }
                    }
                    if (cbtClicked) break;
                } catch (Exception e) {}
            }
            
            if (!cbtClicked) {
                System.out.println("  ✗ Failed to click CBT button");
                return;
            }
            
            // Click OK button (Red)
            try {
                WebElement okBtn = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//button[@type='button' and contains(@class, 'btn-danger') and contains(text(), 'OK')]")));
                js.executeScript("arguments[0].click();", okBtn);
                System.out.println("  ✓ Clicked: OK Button");
                sleep(3);
            } catch (Exception e) {
                System.out.println("  ✗ Failed OK button");
            }
            
        } catch (Exception e) {
            System.out.println("  ⚠ Error returning to CBT section");
        }
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
                    System.out.println("  ✓ Closed payment window");
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
            System.out.println("  ⚠ Issue closing payment window");
        }
    }

    private static void clickElement(By locator, String name) {
        try {
            WebElement elem = wait.until(ExpectedConditions.presenceOfElementLocated(locator));
            js.executeScript("arguments[0].scrollIntoView({block: 'center'});", elem);
            sleep(1);
            js.executeScript("arguments[0].click();", elem);
            System.out.println("  ✓ Clicked: " + name);
        } catch (Exception e) {
            System.out.println("  ✗ Failed to click: " + name);
        }
    }

    private static void enterText(By locator, String text, String fieldName) {
        try {
            WebElement elem = wait.until(ExpectedConditions.presenceOfElementLocated(locator));
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
            html.append(".header { background: white; border-radius: 20px; padding: 40px; margin-bottom: 30px; box-shadow: 0 10px 40px rgba(0,0,0,0.1); text-align: center; }\n");
            html.append(".header h1 { color: #2d3748; font-size: 42px; font-weight: 700; margin-bottom: 10px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }\n");
            html.append(".header .subtitle { color: #718096; font-size: 16px; margin-top: 5px; }\n");
            html.append(".summary { background: white; border-radius: 20px; padding: 40px; margin-bottom: 30px; box-shadow: 0 10px 40px rgba(0,0,0,0.1); }\n");
            html.append(".summary h2 { color: #2d3748; font-size: 28px; font-weight: 600; margin-bottom: 25px; }\n");
            html.append(".stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 20px; margin-bottom: 25px; }\n");
            html.append(".stat-card { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px; border-radius: 15px; box-shadow: 0 5px 20px rgba(102, 126, 234, 0.4); }\n");
            html.append(".stat-card .label { font-size: 14px; opacity: 0.9; margin-bottom: 10px; }\n");
            html.append(".stat-card .value { font-size: 48px; font-weight: 700; }\n");
            html.append(".stat-card.success { background: linear-gradient(135deg, #48bb78 0%, #38a169 100%); }\n");
            html.append(".stat-card.failed { background: linear-gradient(135deg, #f56565 0%, #e53e3e 100%); }\n");
            html.append(".results { background: white; border-radius: 20px; padding: 40px; margin-bottom: 30px; box-shadow: 0 10px 40px rgba(0,0,0,0.1); }\n");
            html.append(".results h2 { color: #2d3748; font-size: 28px; font-weight: 600; margin-bottom: 25px; }\n");
            html.append("table { width: 100%; border-collapse: collapse; }\n");
            html.append("thead { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; }\n");
            html.append("th { padding: 15px; text-align: left; font-weight: 600; }\n");
            html.append("tbody tr { border-bottom: 1px solid #e2e8f0; transition: background 0.3s; }\n");
            html.append("tbody tr:hover { background: #f7fafc; }\n");
            html.append("td { padding: 15px; }\n");
            html.append(".status-badge { display: inline-block; padding: 6px 12px; border-radius: 20px; font-size: 12px; font-weight: 600; }\n");
            html.append(".status-success { background: #c6f6d5; color: #22543d; }\n");
            html.append(".status-failed { background: #fed7d7; color: #742a2a; }\n");
            html.append(".screenshot-link { color: #667eea; text-decoration: none; font-weight: 600; }\n");
            html.append(".screenshot-link:hover { text-decoration: underline; }\n");
            html.append(".error-msg { color: #e53e3e; font-size: 12px; font-style: italic; }\n");
            html.append(".footer { text-align: center; color: white; margin-top: 40px; padding: 20px; }\n");
            html.append("@media (max-width: 768px) { .header h1 { font-size: 32px; } .summary, .results { padding: 25px 20px; } table { font-size: 14px; } th, td { padding: 10px; } }\n");
            html.append("</style>\n</head>\n<body>\n");
            html.append("<div class='container'>\n");
            
            html.append("<div class='header'>\n");
            html.append("<h1>🎯 DAMS CBT Automation Report</h1>\n");
            html.append("<p class='subtitle'>Comprehensive CBT Course Purchase Summary</p>\n");
            html.append("</div>\n");
            
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
            
            html.append("<div class='stat-card failed'>\n");
            html.append("<div class='label'>Failed Attempts</div>\n");
            html.append("<div class='value'>").append(totalFailed).append("</div>\n");
            html.append("</div>\n");
            
            html.append("</div>\n");
            html.append("<p style='margin-top: 20px; color: #4a5568;'><strong>Execution Time:</strong> " + executionStartTime + "</p>\n");
            html.append("</div>\n");
            
            html.append("<div class='results'>\n");
            html.append("<h2>📋 Detailed Results</h2>\n");
            html.append("<table>\n");
            html.append("<thead>\n<tr>\n");
            html.append("<th>#</th>\n<th>Course Name</th>\n<th>Status</th>\n<th>Time</th>\n<th>Screenshot</th>\n<th>Error</th>\n");
            html.append("</tr>\n</thead>\n<tbody>\n");
            
            for (int i = 0; i < courseResults.size(); i++) {
                CourseResult result = courseResults.get(i);
                html.append("<tr>\n");
                html.append("<td>").append(i + 1).append("</td>\n");
                html.append("<td>").append(result.courseName).append("</td>\n");
                
                String statusClass = result.status.equals("SUCCESS") ? "status-success" : "status-failed";
                html.append("<td><span class='status-badge ").append(statusClass).append("'>").append(result.status).append("</span></td>\n");
                
                html.append("<td>").append(result.timestamp).append("</td>\n");
                
                if (result.screenshotPath != null) {
                    html.append("<td><a href='").append(result.screenshotPath).append("' class='screenshot-link' target='_blank'>View QR</a></td>\n");
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
            
            html.append("</tbody>\n</table>\n</div>\n");
            
            html.append("<div class='footer'>\n");
            html.append("<p>Generated by DAMS CBT Automation System | Powered by Selenium WebDriver</p>\n");
            html.append("</div>\n");
            
            html.append("</div>\n</body>\n</html>");
            
            FileWriter writer = new FileWriter(filename);
            writer.write(html.toString());
            writer.close();
            
            System.out.println("✓ Detailed report saved: " + filename);
            
        } catch (Exception e) {
            System.out.println("✗ Report generation failed: " + e.getMessage());
        }
    }
}
