package valoeghese.epbot;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import tk.valoeghese.zoesteriaconfig.api.ZoesteriaConfig;
import tk.valoeghese.zoesteriaconfig.api.container.Container;

public class Main {
	public static void main(String[] args) throws InterruptedException {
		WebDriver document = new ChromeDriver();

		try {
			// sign in
			document.get("https://www.educationperfect.com/app");
			Thread.sleep(1000);
			Container loginInfo = ZoesteriaConfig.loadConfig(new File("login.zfg"));
			document.findElement(By.xpath("//*[@id=\"login-username\"]")).sendKeys(loginInfo.getStringValue("username"));
			document.findElement(By.xpath("//*[@id=\"login-password\"]")).sendKeys(loginInfo.getStringValue("password"));
			document.findElement(By.xpath("//*[@id=\"login-submit-button\"]")).click();
			WebDriverWait wait = new WebDriverWait(document, 30);
			wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("score-value")));
			Thread.sleep(500);

			// Select Infinity TODO 
			// document.findElement(By.xpath("//*[@id=\"number-of-questions-selector\"]/li[5]/div")).click();

			
			// load EP list
			document.get("https://www.educationperfect.com/app/#/Latin/516/499901/list-starter");
			wait.until(ExpectedConditions.visibilityOfElementLocated(FULL_LIST_SWITCH));

			document.findElement(FULL_LIST_SWITCH).click();
			wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@id=\"34632\"]")));

			// wait for sh1t to load
			Thread.sleep(4000);

			// Find Lang Entries
			Map<String, String> languageData = new HashMap<>();

			int i = 0;
			for (WebElement element : loadPage(document)) {
				appendData(languageData, element);
				if (++i % 100 == 0) {
					System.out.println("Loaded " + i + " words.");
				}
			}

			System.out.println("Finished loading words.");
			document.findElement(START_BUTTON).click();

			WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(INPUT));
			Thread.sleep(100);

			for (int j = 0; j < 200; ++j) {
				try {
					String value = document.findElement(TEXT).getAttribute("innerText");
					input.sendKeys(languageData.get(value.toUpperCase(Locale.ROOT)));
					input.sendKeys(Keys.ENTER);
					Thread.sleep(20);
				} catch (Exception e) {
					e.printStackTrace(System.err);
				}
			}

			Thread.sleep(1000 * 10);
		} catch (Throwable t) {
			t.printStackTrace(System.err);
		} finally {
			document.quit();
		}
	}

	private static Collection<WebElement> loadPage(WebDriver browser) throws InterruptedException {
		int searchIndex = 0;
		List<WebElement> elements = browser.findElements(LANG_ENTRY);

		while (searchIndex < elements.size()) {
			searchIndex = elements.size();
			((JavascriptExecutor) browser).executeScript("arguments[0].scrollIntoView();", elements.get(searchIndex - 1));
			Thread.sleep(100);
			elements = browser.findElements(LANG_ENTRY);
		}

		System.out.println("Found " + elements.size() + " words.");
		return elements;
	}

	private static void appendData(Map<String, String> words, WebElement element) {
		String targetLang = element.findElements(By.className("targetLanguage")).get(0).getAttribute("innerText").toUpperCase(Locale.ROOT);
		String baseLang = element.findElements(By.className("baseLanguage")).get(0).getAttribute("innerText").toUpperCase(Locale.ROOT);
		words.put(targetLang, baseLang);
	}

	private static final By LANG_ENTRY = By.className("preview-grid-item-content");
	private static final By START_BUTTON = By.id("start-button-main");
	private static final String SUBMIT_BUTTON = "submit-button";
	private static final By FULL_LIST_SWITCH = By.xpath("//*[@id=\"full-list-switcher\"]");
	private static final By TEXT = By.xpath("//*[@id=\"question-text\"]/span");
	private static final By INPUT = By.xpath("/html/body/div[1]/div[2]/div/ui-view/div[1]/div[2]/div/div/div[2]/div[2]/game-lp-answer-input/div/div[2]/input");

	//private static final By SCROLLBAR = By.xpath("//*[@id=\"preview-grid-container\"]/div[2]");
}
