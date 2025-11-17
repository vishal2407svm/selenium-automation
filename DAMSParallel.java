import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DAMSParallel {
    
    // Configuration - will be loaded from config.properties in GitHub Actions
    private static String[] PHONE_NUMBERS;
    private static int NUM_TABS;
    private static String OTP;
    
    // Global synchronization
    private static final Object NETWORK_LOCK = new Object();
    
    // Thread-safe data structures
    private static final Map<String, List<ScreenshotInfo>> courseQRScreenshots = new ConcurrentHashMap<>();
    private static final AtomicInteger totalCoursesProcessed = new AtomicInteger(0);
    private static final AtomicInteger totalPackagesProcessed = new AtomicInteger(0);
    
    // Date formatters
    private static SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private static SimpleDateFormat fileFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
    
    static class ScreenshotInfo implements Comparable<ScreenshotInfo> {
        String filepath;
        int packageIndex;
        int tabNumber;
        String timestamp;
        
        ScreenshotInfo(String filepath, int packageIndex, int tabNumber, String timestamp) {
            this.filepath = filepath;
            this.packageIndex = packageIndex;
            this.tabNumber = tabNumber;
            this.timestamp = timestamp;
        }
        
        @Override
        public int compareTo(ScreenshotInfo other) {
            return Integer.compare(this.packageIndex, other.packageIndex);
        }
    }
    
    static class TabResult {
        int coursesProcessed;
        int packagesProcessed;
        
        TabResult(int courses, int packages) {
            this.coursesProcessed = courses;
            this.packagesProcessed = packages;
        }
    }
    
    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Load configuration
            loadConfiguration();
            
            new File("screenshots").mkdirs();
            
            System.out.println("╔════════════════════════════════════════════╗");
            System.out.println("  DAMS 4-TAB PARALLEL AUTOMATION");
            System.out.println("╚════════════════════════════════════════════╝\n");
            
            // PHASE 1: Master tab discovers all courses
            System.out.println("🔍 PHASE 1: Discovering courses...\n");
            
            WebDriver masterDriver = setupDriver();
            login(masterDriver, PHONE_NUMBERS[0], 0);
            
            List<String> allCourses = discoverCoursesFromDropdown(masterDriver);
            
            System.out.println("\n✓ Found " + allCourses.size() + " courses:");
            for (int i = 0; i < allCourses.size(); i++) {
                System.out.println("  [" + (i + 1) + "] " + allCourses.get(i));
            }
            
            masterDriver.quit();
            System.out.println("\n✓ Master tab closed");
            
            // PHASE 2: Distribute courses evenly
            System.out.println("\n📊 PHASE 2: Distributing courses...\n");
            
            List<List<String>> courseDistribution = distributeCoursesEvenly(allCourses, NUM_TABS);
            
            for (int i = 0; i < courseDistribution.size(); i++) {
                System.out.println("Tab " + (i + 1) + " (" + PHONE_NUMBERS[i] + "): " + 
                                 courseDistribution.get(i).size() + " courses");
                System.out.println("  → " + courseDistribution.get(i));
            }
            
            // PHASE 3: Parallel processing
            System.out.println("\n" + "=".repeat(60));
            System.out.println("🚀 PHASE 3: STARTING PARALLEL PROCESSING");
            System.out.println("=".repeat(60) + "\n");
            
            ExecutorService executor = Executors.newFixedThreadPool(NUM_TABS);
            List<Future<TabResult>> futures = new ArrayList<>();
            
            // Launch all tabs with staggered start (3 seconds apart)
            for (int tabIdx = 0; tabIdx < NUM_TABS; tabIdx++) {
                final int tabNumber = tabIdx + 1;
                final List<String> assignedCourses = courseDistribution.get(tabIdx);
                final String phoneNumber = PHONE_NUMBERS[tabIdx];
                final int startDelay = tabIdx * 3;
                
                Future<TabResult> future = executor.submit(() -> {
                    if (startDelay > 0) {
                        sleep(startDelay);
                    }
                    return processTabCourses(tabNumber, phoneNumber, assignedCourses);
                });
                
                futures.add(future);
            }
            
            // Wait for all tabs to complete
            System.out.println("⏳ Waiting for all tabs to complete...\n");
            
            for (int i = 0; i < futures.size(); i++) {
                try {
                    TabResult result = futures.get(i).get(2, TimeUnit.HOURS);
                    System.out.println("\n✅ Tab " + (i + 1) + " COMPLETED: " + 
                                     result.coursesProcessed + " courses, " + 
                                     result.packagesProcessed + " packages");
                } catch (Exception e) {
                    System.out.println("\n❌ Tab " + (i + 1) + " FAILED: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.MINUTES);
            
            // Calculate execution time
            long endTime = System.currentTimeMillis();
            long durationSeconds = (endTime - startTime) / 1000;
            
            System.out.println("\n╔════════════════════════════════════════════╗");
            System.out.println("  ✓ ALL TABS COMPLETED!");
            System.out.println("  Total Courses: " + totalCoursesProcessed.get());
            System.out.println("  Total Packages: " + totalPackagesProcessed.get());
            System.out.println("  Execution Time: " + formatDuration(durationSeconds));
            System.out.println("  Estimated Speedup: ~" + NUM_TABS + "x faster");
            System.out.println("╚════════════════════════════════════════════╝");
            
        } catch (Exception e) {
            System.out.println("\n❌ FATAL ERROR: " + e.getMessage());
            e.printStackTrace();
        } finally {
            generateReport();
            System.out.println("\n✓ Report generated successfully!");
        }
    }
    
    private static void loadConfiguration() {
        try {
            Properties props = new Properties();
            
            // Try to load from config.properties file (for GitHub Actions)
            File configFile = new File("config.properties");
            if (configFile.exists()) {
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    props.load(fis);
                    
                    String phoneNumbers = props.getProperty("phone.numbers");
                    PHONE_NUMBERS = phoneNumbers.split(",");
                    
                    OTP = props.getProperty("otp", "2000");
                    NUM_TABS = Integer.parseInt(props.getProperty("num.tabs", "4"));
                    
                    System.out.println("✓ Configuration loaded from config.properties");
                    System.out.println("  Phone numbers: " + Arrays.toString(PHONE_NUMBERS));
                    System.out.println("  Number of tabs: " + NUM_TABS);
                    System.out.println();
                }
            } else {
                // Default configuration (for local execution)
                PHONE_NUMBERS = new String[]{
                    "+919456628016",
                    "+919289790436",
                    "+917564012375",
                    "+919411611466"
                };
                NUM_TABS = 4;
                OTP = "2000";
                
                System.out.println("⚠️  No config.properties found, using default configuration");
                System.out.println();
            }
        } catch (Exception e) {
            System.out.println("❌ Error loading configuration: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static TabResult processTabCourses(int tabNumber, String phoneNumber, List<String> courses) {
        WebDriver driver = null;
        WebDriverWait wait = null;
        JavascriptExecutor js = null;
        int coursesProcessed = 0;
        int packagesProcessed = 0;
        
        try {
            System.out.println("[Tab " + tabNumber + "] 🚀 STARTING with " + courses.size() + " courses");
            
            driver = setupDriver();
            wait = new WebDriverWait(driver, Duration.ofSeconds(30));
            js = (JavascriptExecutor) driver;
            
            login(driver, phoneNumber, tabNumber);
            
            for (String courseName : courses) {
                try {
                    System.out.println("\n[Tab " + tabNumber + "] " + "═".repeat(40));
                    System.out.println("[Tab " + tabNumber + "] 📚 COURSE: " + courseName);
                    System.out.println("[Tab " + tabNumber + "] " + "═".repeat(40));
                    
                    List<ScreenshotInfo> screenshots = processCourse(driver, wait, js, courseName, tabNumber);
                    
                    synchronized(courseQRScreenshots) {
                        courseQRScreenshots.put(courseName, screenshots);
                    }
                    
                    coursesProcessed++;
                    packagesProcessed += screenshots.size();
                    totalCoursesProcessed.incrementAndGet();
                    totalPackagesProcessed.addAndGet(screenshots.size());
                    
                    System.out.println("[Tab " + tabNumber + "] ✅ Course Complete: " + courseName + 
                                     " (" + screenshots.size() + " packages)");
                    
                } catch (Exception e) {
                    System.out.println("[Tab " + tabNumber + "] ❌ Error: " + courseName + " - " + e.getMessage());
                }
            }
            
            System.out.println("\n[Tab " + tabNumber + "] 🏁 ALL ASSIGNED COURSES DONE!");
            
        } catch (Exception e) {
            System.out.println("[Tab " + tabNumber + "] ❌ Fatal Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                    System.out.println("[Tab " + tabNumber + "] 🔒 Browser closed");
                } catch (Exception e) {}
            }
        }
        
        return new TabResult(coursesProcessed, packagesProcessed);
    }
    
    private static List<ScreenshotInfo> processCourse(WebDriver driver, WebDriverWait wait, 
                                                      JavascriptExecutor js, String courseName, int tabNumber) {
        List<ScreenshotInfo> screenshots = new ArrayList<>();
        
        try {
            selectCourse(driver, wait, js, courseName, tabNumber);
            clickGoProButton(driver, wait, js, tabNumber);
            List<WebElement> packageButtons = findAllPackageButtons(driver, js, tabNumber);
            int packageCount = packageButtons.size();
            
            System.out.println("[Tab " + tabNumber + "]   → Found " + packageCount + " packages");
            
            if (packageCount == 0) {
                System.out.println("[Tab " + tabNumber + "]   ⚠️  No packages found, skipping");
                return screenshots;
            }
            
            for (int pkgIdx = 0; pkgIdx < packageCount; pkgIdx++) {
                System.out.println("[Tab " + tabNumber + "]   📦 Package [" + (pkgIdx+1) + "/" + packageCount + "]");
                
                if (pkgIdx > 0) {
                    synchronized(NETWORK_LOCK) {
                        driver.get("https://www.damsdelhi.com/");
                        sleep(2);
                    }
                    clickGoProButton(driver, wait, js, tabNumber);
                    packageButtons = findAllPackageButtons(driver, js, tabNumber);
                }
                
                if (pkgIdx < packageButtons.size()) {
                    WebElement pkgButton = packageButtons.get(pkgIdx);
                    js.executeScript("arguments[0].scrollIntoView({block: 'center'});", pkgButton);
                    sleep(1);
                    
                    synchronized(NETWORK_LOCK) {
                        js.executeScript("arguments[0].click();", pkgButton);
                        System.out.println("[Tab " + tabNumber + "]     ✓ Clicked package");
                        sleep(2);
                    }
                    
                    ScreenshotInfo screenshot = processPackageCheckout(driver, wait, js, courseName, pkgIdx, tabNumber);
                    if (screenshot != null) {
                        screenshots.add(screenshot);
                    }
                    
                    synchronized(NETWORK_LOCK) {
                        driver.get("https://www.damsdelhi.com/");
                        sleep(2);
                    }
                }
            }
            
            Collections.sort(screenshots);
            
        } catch (Exception e) {
            System.out.println("[Tab " + tabNumber + "]   ❌ Course error: " + e.getMessage());
        }
        
        return screenshots;
    }
    
    private static ScreenshotInfo processPackageCheckout(WebDriver driver, WebDriverWait wait, 
                                                         JavascriptExecutor js, String courseName, 
                                                         int packageIndex, int tabNumber) {
        try {
            try {
                List<WebElement> durations = driver.findElements(By.xpath("//h3[contains(text(), 'Month')]"));
                if (!durations.isEmpty()) {
                    int durIdx = Math.min(packageIndex, durations.size() - 1);
                    WebElement duration = durations.get(durIdx);
                    js.executeScript("arguments[0].scrollIntoView({block: 'center'});", duration);
                    sleep(1);
                    js.executeScript("arguments[0].click();", duration);
                    System.out.println("[Tab " + tabNumber + "]     ✓ Selected duration");
                    sleep(1);
                }
            } catch (Exception e) {}
            
            handleYesPopup(driver, js, tabNumber);
            
            By[] continueSelectors = {
                By.xpath("//button[@type='button' and contains(@class, 'BtnNewCreate')]"),
                By.xpath("//button[contains(text(), 'Continue')]"),
                By.xpath("//button[contains(@class, 'ant-btn-primary') and contains(@class, 'ant-btn-block')]")
            };
            
            for (By selector : continueSelectors) {
                try {
                    WebElement continueBtn = wait.until(ExpectedConditions.presenceOfElementLocated(selector));
                    js.executeScript("arguments[0].scrollIntoView({block: 'center'});", continueBtn);
                    sleep(1);
                    
                    synchronized(NETWORK_LOCK) {
                        js.executeScript("arguments[0].click();", continueBtn);
                        System.out.println("[Tab " + tabNumber + "]     ✓ Clicked Continue");
                        sleep(2);
                    }
                    break;
                } catch (Exception e) {}
            }
            
            if (packageIndex > 0) {
                handleYesPopup(driver, js, tabNumber);
            }
            
            try {
                WebElement logoutBtn = driver.findElement(
                    By.xpath("//button[contains(@class, 'btndata') and contains(text(), 'Logout')]"));
                js.executeScript("arguments[0].click();", logoutBtn);
                sleep(2);
            } catch (Exception e) {}
            
            WebElement otpInput = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//input[@type='text' or @type='number' or contains(@placeholder, 'OTP')]")));
            otpInput.clear();
            otpInput.sendKeys(OTP);
            sleep(1);
            
            WebElement submitBtn = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.className("common-bottom-btn")));
            js.executeScript("arguments[0].click();", submitBtn);
            sleep(5);
            
            if (tabNumber == 0) {
                System.out.println("✓ Master login successful\n");
            } else {
                System.out.println("[Tab " + tabNumber + "] ✓ Login successful");
            }
            
        } catch (Exception e) {
            System.out.println("[Tab " + tabNumber + "] ❌ Login failed: " + e.getMessage());
        }
    }
    
    private static List<String> discoverCoursesFromDropdown(WebDriver driver) {
        System.out.println("🔍 Discovering courses from dropdown...");
        List<String> courseNames = new ArrayList<>();
        
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            js.executeScript("window.scrollTo(0, 0);");
            sleep(2);
            
            WebElement dropdown = null;
            By[] dropdownSelectors = {
                By.xpath("//button[contains(@class, 'SelectCat')]"),
                By.xpath("//button[contains(@class, 'SelectCat') and contains(text(), 'FMGE')]")
            };
            
            for (By selector : dropdownSelectors) {
                try {
                    dropdown = wait.until(ExpectedConditions.presenceOfElementLocated(selector));
                    break;
                } catch (Exception e) {}
            }
            
            if (dropdown == null) {
                System.out.println("❌ Dropdown not found!");
                return courseNames;
            }
            
            js.executeScript("arguments[0].click();", dropdown);
            System.out.println("  ✓ Opened dropdown");
            sleep(3);
            
            try {
                List<WebElement> scrollables = driver.findElements(
                    By.xpath("//div[contains(@class, 'ant-modal-body') or contains(@class, 'ant-dropdown')]"));
                for (WebElement scrollable : scrollables) {
                    for (int i = 0; i < 5; i++) {
                        js.executeScript("arguments[0].scrollTop = arguments[0].scrollHeight", scrollable);
                        sleep(1);
                    }
                }
            } catch (Exception e) {}
            
            sleep(2);
            
            Set<String> uniqueCourses = new LinkedHashSet<>();
            
            List<By> courseSelectors = Arrays.asList(
                By.xpath("//div[contains(@class, 'ant-dropdown') or contains(@class, 'ant-modal')]//span[string-length(normalize-space(text())) > 2]"),
                By.xpath("//div[contains(@class, 'ant-modal-body')]//span[string-length(normalize-space(text())) > 2]")
            );
            
            for (By selector : courseSelectors) {
                try {
                    List<WebElement> elements = driver.findElements(selector);
                    for (WebElement elem : elements) {
                        if (elem.isDisplayed()) {
                            String text = elem.getText().trim();
                            
                            if (isValidCourseName(text)) {
                                uniqueCourses.add(text);
                            }
                        }
                    }
                } catch (Exception e) {}
            }
            
            courseNames.addAll(uniqueCourses);
            
            try {
                driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
                sleep(1);
            } catch (Exception e) {}
            
            System.out.println("  ✓ Collected " + courseNames.size() + " valid courses");
            
        } catch (Exception e) {
            System.out.println("❌ Error discovering courses: " + e.getMessage());
        }
        
        return courseNames;
    }
    
    private static boolean isValidCourseName(String text) {
        if (text.length() < 4) return false;
        
        String lower = text.toLowerCase();
        
        String[] invalidTerms = {
            "home", "logout", "close", "sign in", "sign out", 
            "login", "cart", "menu", "search", "back", "next", 
            "previous", "submit", "ok", "yes", "no", "cancel",
            "noida", "delhi", "mumbai", "bangalore", "chennai", "kolkata",
            "free", "premium", "pro", "basic", "access", "locked", "unlocked",
            "select", "choose", "please select"
        };
        
        for (String invalid : invalidTerms) {
            if (lower.equals(invalid)) return false;
        }
        
        if (!text.matches(".*[A-Za-z].*[A-Za-z].*")) return false;
        
        if (!text.contains(" ") && text.length() <= 4 && text.equals(lower)) return false;
        
        return true;
    }
    
    private static List<List<String>> distributeCoursesEvenly(List<String> allCourses, int numTabs) {
        List<List<String>> distribution = new ArrayList<>();
        int totalCourses = allCourses.size();
        int coursesPerTab = (int) Math.ceil((double) totalCourses / numTabs);
        
        for (int i = 0; i < numTabs; i++) {
            int start = i * coursesPerTab;
            int end = Math.min(start + coursesPerTab, totalCourses);
            
            if (start < totalCourses) {
                distribution.add(new ArrayList<>(allCourses.subList(start, end)));
            } else {
                distribution.add(new ArrayList<>());
            }
        }
        
        return distribution;
    }
    
    private static void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private static String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
    }
    
    private static void generateReport() {
        System.out.println("\n📄 Generating HTML report...");
        
        try {
            String timestamp = fileFormat.format(new Date());
            String filename = "DAMS_Parallel_Report_" + timestamp + ".html";
            
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html>\n<html>\n<head>\n");
            html.append("<meta charset='UTF-8'>\n");
            html.append("<title>DAMS Parallel Automation Report</title>\n");
            html.append("<style>\n");
            html.append("body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }\n");
            html.append("h1 { color: #333; border-bottom: 3px solid #4CAF50; padding-bottom: 10px; }\n");
            html.append(".summary { background: white; padding: 20px; margin: 20px 0; border-radius: 5px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }\n");
            html.append(".summary h2 { color: #2196F3; margin-top: 0; }\n");
            html.append(".stats { display: flex; gap: 30px; font-size: 24px; font-weight: bold; flex-wrap: wrap; }\n");
            html.append(".stat-item { padding: 20px; background: #4CAF50; color: white; border-radius: 5px; min-width: 200px; }\n");
            html.append(".course-section { background: white; padding: 20px; margin: 20px 0; border-radius: 5px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }\n");
            html.append(".course-section h2 { color: #FF5722; border-bottom: 2px solid #FF5722; padding-bottom: 5px; }\n");
            html.append(".qr-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px; margin-top: 20px; }\n");
            html.append(".qr-item { text-align: center; padding: 10px; background: #f9f9f9; border-radius: 5px; border: 2px solid #ddd; }\n");
            html.append(".qr-item img { max-width: 100%; height: auto; border: 2px solid #ddd; border-radius: 5px; }\n");
            html.append(".qr-item p { font-weight: bold; margin: 10px 0; color: #333; }\n");
            html.append(".qr-item .tab-info { font-size: 12px; color: #666; margin-top: 5px; }\n");
            html.append(".badge { display: inline-block; padding: 5px 10px; background: #2196F3; color: white; border-radius: 3px; font-size: 12px; margin-top: 5px; }\n");
            html.append("</style>\n</head>\n<body>\n");
            
            html.append("<h1>🚀 DAMS 4-Tab Parallel Automation Report</h1>\n");
            
            html.append("<div class='summary'>\n");
            html.append("<h2>📊 Summary</h2>\n");
            html.append("<div class='stats'>\n");
            html.append("<div class='stat-item'>Total Courses: ").append(totalCoursesProcessed.get()).append("</div>\n");
            html.append("<div class='stat-item'>Total Packages: ").append(totalPackagesProcessed.get()).append("</div>\n");
            html.append("<div class='stat-item'>Parallel Tabs: ").append(NUM_TABS).append("</div>\n");
            html.append("</div>\n");
            html.append("<p><strong>Generated:</strong> ").append(timestamp).append("</p>\n");
            html.append("<p><strong>Phone Numbers Used:</strong></p>\n<ul>\n");
            for (int i = 0; i < PHONE_NUMBERS.length; i++) {
                html.append("<li>Tab ").append(i + 1).append(": ").append(PHONE_NUMBERS[i]).append("</li>\n");
            }
            html.append("</ul>\n");
            html.append("</div>\n");
            
            List<String> sortedCourseNames = new ArrayList<>(courseQRScreenshots.keySet());
            Collections.sort(sortedCourseNames);
            
            for (String courseName : sortedCourseNames) {
                List<ScreenshotInfo> screenshots = courseQRScreenshots.get(courseName);
                
                html.append("<div class='course-section'>\n");
                html.append("<h2>📚 ").append(courseName).append("</h2>\n");
                html.append("<p><strong>Packages processed:</strong> ").append(screenshots.size()).append("</p>\n");
                
                if (!screenshots.isEmpty()) {
                    html.append("<div class='qr-grid'>\n");
                    
                    for (ScreenshotInfo screenshot : screenshots) {
                        html.append("<div class='qr-item'>\n");
                        html.append("<p>Package ").append(screenshot.packageIndex + 1).append("</p>\n");
                        html.append("<img src='").append(screenshot.filepath).append("' alt='QR Code Package ").append(screenshot.packageIndex + 1).append("'>\n");
                        html.append("<div class='tab-info'>\n");
                        html.append("<span class='badge'>Tab ").append(screenshot.tabNumber).append("</span>\n");
                        html.append("<br>").append(screenshot.timestamp).append("\n");
                        html.append("</div>\n");
                        html.append("</div>\n");
                    }
                    
                    html.append("</div>\n");
                } else {
                    html.append("<p><em>No packages processed for this course.</em></p>\n");
                }
                
                html.append("</div>\n");
            }
            
            html.append("</body>\n</html>");
            
            FileWriter writer = new FileWriter(filename);
            writer.write(html.toString());
            writer.close();
            
            System.out.println("✅ Report saved: " + filename);
            
        } catch (Exception e) {
            System.out.println("❌ Report generation failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}Element checkoutBtn = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//button[contains(@class, 'btn-danger') and contains(@class, 'btn-block')]")));
                js.executeScript("arguments[0].scrollIntoView({block: 'center'});", checkoutBtn);
                sleep(1);
                
                synchronized(NETWORK_LOCK) {
                    js.executeScript("arguments[0].click();", checkoutBtn);
                    System.out.println("[Tab " + tabNumber + "]     ✓ Clicked Checkout");
                    sleep(2);
                }
            } catch (Exception e) {}
            
            try {
                WebElement paytm = null;
                By[] paytmSelectors = {
                    By.xpath("//label[.//span[contains(text(), 'Paytm')]]"),
                    By.xpath("//span[contains(@class, 'ant-radio') and contains(text(), 'Paytm')]/parent::label")
                };
                
                for (By selector : paytmSelectors) {
                    try {
                        paytm = wait.until(ExpectedConditions.presenceOfElementLocated(selector));
                        break;
                    } catch (Exception e) {}
                }
                
                if (paytm != null) {
                    js.executeScript("arguments[0].click();", paytm);
                    System.out.println("[Tab " + tabNumber + "]     ✓ Selected Paytm");
                    sleep(1);
                }
            } catch (Exception e) {}
            
            try {
                WebElement paymentBtn = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//button[@type='button' and contains(@class, 'ant-btn-primary') and contains(@class, 'ant-btn-block')]")));
                
                synchronized(NETWORK_LOCK) {
                    js.executeScript("arguments[0].click();", paymentBtn);
                    System.out.println("[Tab " + tabNumber + "]     ✓ Clicked Payment");
                    sleep(2);
                }
            } catch (Exception e) {}
            
            System.out.println("[Tab " + tabNumber + "]     ⏳ Waiting 30s for QR code...");
            sleep(30);
            
            String timestamp = fileFormat.format(new Date());
            String cleanCourseName = courseName.replaceAll("[^a-zA-Z0-9]", "_");
            String filename = "screenshots/QR_" + cleanCourseName + 
                            "_pkg" + (packageIndex + 1) + 
                            "_Tab" + tabNumber + 
                            "_" + timestamp + ".png";
            
            File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            FileUtils.copyFile(screenshot, new File(filename));
            System.out.println("[Tab " + tabNumber + "]     📸 Screenshot saved: " + filename);
            
            closePaymentWindow(driver, js, tabNumber);
            
            return new ScreenshotInfo(filename, packageIndex, tabNumber, timestamp);
            
        } catch (Exception e) {
            System.out.println("[Tab " + tabNumber + "]     ❌ Checkout error: " + e.getMessage());
            return null;
        }
    }
    
    private static void selectCourse(WebDriver driver, WebDriverWait wait, JavascriptExecutor js, 
                                    String courseName, int tabNumber) {
        try {
            js.executeScript("window.scrollTo(0, 0);");
            sleep(1);
            
            synchronized(NETWORK_LOCK) {
                WebElement dropdown = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//button[contains(@class, 'SelectCat')]")));
                js.executeScript("arguments[0].click();", dropdown);
                sleep(2);
            }
            
            List<WebElement> courseOptions = driver.findElements(
                By.xpath("//span[normalize-space(text())='" + courseName + "']"));
            
            for (WebElement option : courseOptions) {
                if (option.isDisplayed()) {
                    synchronized(NETWORK_LOCK) {
                        js.executeScript("arguments[0].click();", option);
                        System.out.println("[Tab " + tabNumber + "]   ✓ Selected course: " + courseName);
                        sleep(2);
                    }
                    break;
                }
            }
            
            try {
                WebElement closeBtn = driver.findElement(
                    By.xpath("//button[@type='button' and @aria-label='Close' and contains(@class, 'ant-modal-close')]"));
                js.executeScript("arguments[0].click();", closeBtn);
                sleep(1);
            } catch (Exception e) {}
            
        } catch (Exception e) {
            System.out.println("[Tab " + tabNumber + "]   ❌ Error selecting course: " + e.getMessage());
        }
    }
    
    private static void clickGoProButton(WebDriver driver, WebDriverWait wait, JavascriptExecutor js, int tabNumber) {
        try {
            js.executeScript("window.scrollTo(0, 0);");
            sleep(1);
            
            synchronized(NETWORK_LOCK) {
                WebElement goProBtn = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//strong[contains(text(), 'Go Pro')]")));
                js.executeScript("arguments[0].click();", goProBtn);
                System.out.println("[Tab " + tabNumber + "]   ✓ Clicked Go Pro");
                sleep(2);
            }
            
            js.executeScript("window.scrollTo(0, 0);");
            sleep(1);
            long lastHeight = (Long) js.executeScript("return document.body.scrollHeight");
            int stableCount = 0;
            
            while (stableCount < 2) {
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
            sleep(1);
            
        } catch (Exception e) {
            System.out.println("[Tab " + tabNumber + "]   ❌ Error clicking Go Pro: " + e.getMessage());
        }
    }
    
    private static List<WebElement> findAllPackageButtons(WebDriver driver, JavascriptExecutor js, int tabNumber) {
        List<WebElement> buttons = new ArrayList<>();
        
        try {
            List<By> buttonSelectors = Arrays.asList(
                By.xpath("//button[@type='button' and contains(@class, 'BtnNewCreate')]"),
                By.xpath("//button[contains(text(), 'Buy') or contains(text(), 'Select') or contains(text(), 'Choose')]"),
                By.xpath("//a[contains(text(), 'Buy') or contains(text(), 'Select') or contains(text(), 'Choose')]"),
                By.xpath("//*[contains(@class, 'btn') and (contains(text(), 'Buy') or contains(text(), 'Select'))]")
            );
            
            for (By selector : buttonSelectors) {
                try {
                    List<WebElement> found = driver.findElements(selector);
                    for (WebElement btn : found) {
                        if (btn.isDisplayed()) {
                            buttons.add(btn);
                        }
                    }
                    if (!buttons.isEmpty()) break;
                } catch (Exception e) {}
            }
            
            if (buttons.isEmpty()) {
                List<WebElement> cards = driver.findElements(
                    By.xpath("//div[contains(@class, 'card') or contains(@class, 'col')]//button | //div[contains(@class, 'card') or contains(@class, 'col')]//a"));
                for (WebElement card : cards) {
                    if (card.isDisplayed()) {
                        buttons.add(card);
                    }
                }
            }
            
        } catch (Exception e) {
            System.out.println("[Tab " + tabNumber + "]   ❌ Error finding packages: " + e.getMessage());
        }
        
        return buttons;
    }
    
    private static void handleYesPopup(WebDriver driver, JavascriptExecutor js, int tabNumber) {
        try {
            By[] yesSelectors = {
                By.xpath("//button[@type='button']//span[contains(text(), 'Yes')]"),
                By.xpath("//button[contains(@class, 'ant-btn')]//span[text()='Yes']"),
                By.xpath("//span[text()='Yes']/parent::button"),
                By.xpath("//button[contains(text(), 'Yes')]")
            };
            
            for (By selector : yesSelectors) {
                try {
                    WebElement yesBtn = driver.findElement(selector);
                    if (yesBtn.isDisplayed()) {
                        js.executeScript("arguments[0].click();", yesBtn);
                        System.out.println("[Tab " + tabNumber + "]     ✓ Clicked Yes popup");
                        sleep(1);
                        return;
                    }
                } catch (Exception e) {}
            }
        } catch (Exception e) {}
    }
    
    private static void closePaymentWindow(WebDriver driver, JavascriptExecutor js, int tabNumber) {
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
                    System.out.println("[Tab " + tabNumber + "]     ✓ Closed payment window");
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
            
        } catch (Exception e) {}
    }
    
    private static WebDriver setupDriver() {
        ChromeOptions options = new ChromeOptions();
        
        // GitHub Actions environment detection
        String ciEnv = System.getenv("CI");
        if ("true".equals(ciEnv)) {
            System.out.println("🔧 Detected GitHub Actions environment");
            options.addArguments("--headless=new");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            
            // Use system ChromeDriver in GitHub Actions
            System.setProperty("webdriver.chrome.driver", "/usr/local/bin/chromedriver");
        } else {
            // Local environment
            System.setProperty("webdriver.chrome.driver", "chromedriver.exe");
            options.addArguments("--start-maximized");
        }
        
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-software-rasterizer");
        
        return new ChromeDriver(options);
    }
    
    private static void login(WebDriver driver, String phoneNumber, int tabNumber) {
        try {
            if (tabNumber == 0) {
                System.out.println("🔐 Logging in (Master Tab)...");
            } else {
                System.out.println("[Tab " + tabNumber + "] 🔐 Logging in with " + phoneNumber + "...");
            }
            
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            synchronized(NETWORK_LOCK) {
                driver.get("https://www.damsdelhi.com/");
                sleep(3);
            }
            
            try {
                WebElement signInBtn = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//button[contains(text(), 'Sign in') or contains(text(), 'Sign In')]")));
                js.executeScript("arguments[0].click();", signInBtn);
                sleep(2);
            } catch (Exception e) {
                try {
                    WebElement signInBtn = wait.until(ExpectedConditions.presenceOfElementLocated(
                        By.xpath("//a[contains(text(), 'Sign in') or contains(text(), 'Sign In')]")));
                    js.executeScript("arguments[0].click();", signInBtn);
                    sleep(2);
                } catch (Exception e2) {
                    System.out.println("[Tab " + tabNumber + "] ⚠️  Sign in button not found");
                }
            }
            
            WebElement phoneInput = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//input[@type='tel' or @type='number' or contains(@placeholder, 'number')]")));
            phoneInput.clear();
            phoneInput.sendKeys(phoneNumber);
            sleep(1);
            
            WebElement otpBtn = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.className("common-bottom-btn")));
            js.executeScript("arguments[0].click();", otpBtn);
            sleep(2);
            
            try {
                Web
