package valoeghese.epbot;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import tk.valoeghese.zoesteriaconfig.api.ZoesteriaConfig;
import tk.valoeghese.zoesteriaconfig.api.container.Container;
import tk.valoeghese.zoesteriaconfig.api.container.EditableContainer;
import tk.valoeghese.zoesteriaconfig.api.container.WritableConfig;
import tk.valoeghese.zoesteriaconfig.impl.parser.ImplZoesteriaDefaultDeserialiser;

public class Main {
	public static void main(String[] args) throws InterruptedException, IOException {
		// Load data
		System.out.println("Starting data load");
		long time = System.currentTimeMillis();
		URL url = new URL("https://raw.githubusercontent.com/valoeghese/valoeghese.github.io/compass/programdata/latin_all.zfg");
		EditableContainer languageData = new StringZFGParser<>(readString(url::openStream), new ImplZoesteriaDefaultDeserialiser(true)).asWritableConfig();
		System.out.println("finished loading data in " + (System.currentTimeMillis() - time) + "ms.");

		// Create Driver for chrome. Requires Chrome Driver to be installed.
		WebDriver document = new ChromeDriver();

		try {
			// sign in
			document.get("https://www.educationperfect.com/app");
			Thread.sleep(1000);
			// load the login information.
			Container loginInfo = ZoesteriaConfig.loadConfig(new File("login.zfg"));
			document.findElement(By.xpath("//*[@id=\"login-username\"]")).sendKeys(loginInfo.getStringValue("username"));
			document.findElement(By.xpath("//*[@id=\"login-password\"]")).sendKeys(loginInfo.getStringValue("password"));
			document.findElement(By.xpath("//*[@id=\"login-submit-button\"]")).click();
			WebDriverWait wait = new WebDriverWait(document, 30);
			wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("score-value")));
			Thread.sleep(500);

			// load EP list
			document.get("https://www.educationperfect.com/app/#/Latin/516/499901/list-starter");
			wait.until(ExpectedConditions.visibilityOfElementLocated(FULL_LIST_SWITCH));

			document.findElement(FULL_LIST_SWITCH).click();
			wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@id=\"34632\"]"))); // wait for the page to load almost fully. Might not be neccesary.

			// select infinite questions
			WebElement infinity = document.findElement(By.xpath("//*[@id=\"number-of-questions-selector\"]/li[5]/div"));
			((JavascriptExecutor) document).executeScript("arguments[0].scrollIntoView();", infinity);
			infinity.click();

			// createDataFile(document, "latin_all");
			// System.exit(0);

			document.findElement(START_BUTTON).click();

			// wait for the page to load, then get input
			WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(INPUT));
			Thread.sleep(100);

			while (true) {
				String prevVal = null;
				try {
					String value = null;

					do {
						try {
							value = document.findElement(TEXT).getAttribute("innerText");
						} catch (NoSuchElementException e) {
							// ignore
						}
					} while (value == null || value.equals(prevVal));

					boolean flag = false;

					value = value.split(",")[0].toLowerCase(Locale.ROOT);
					value = value.replace(' ', '_'); // ZoesteriaConfig doesn't support spaces in keys, so I replaced them with underscores
					String output = (String) languageData.getMap(String.valueOf(value.charAt(0))).get(value);

					if (output == null || output.isEmpty()) {
						output = "?";
						flag = true;
					}

					input.sendKeys(output);
					input.sendKeys(Keys.ENTER);
					prevVal = value;

					if (flag) {
						wait.until(ExpectedConditions.visibilityOfElementLocated(SKIP)).click();
					}

					Thread.sleep(50);
				} catch (Exception e) {
					e.printStackTrace(System.err);
				}
			}
		} catch (Throwable t) {
			t.printStackTrace(System.err);
		} finally {
			document.quit();
		}
	}

	private static void createDataFile(WebDriver document, String fileName) throws InterruptedException {
		// wait for sh1t to load
		Thread.sleep(4000);

		// Find Lang Entries
		Map<Character, Map<String, Object>> languageData = new HashMap<>();

		int i = 0;
		for (WebElement element : loadPage(document)) {
			appendData(languageData, element);
			if (++i % 100 == 0) {
				System.out.println("Loaded " + i + " words.");
			}
		}

		System.out.println("Finished loading words.");
		languageData.computeIfAbsent('a', n -> new HashMap<>()).put("audit", "hears");

		WritableConfig output = ZoesteriaConfig.createWritableConfig(new LinkedHashMap<>());

		languageData.forEach((c, map) -> {
			output.putMap(Character.toString(c), map);
		});

		output.writeToFile(new File("./" + fileName + ".zfg"));
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

	private static void appendData(Map<Character, Map<String, Object>> words, WebElement element) {
		String targetLang = element.findElements(By.className("targetLanguage")).get(0).getAttribute("innerText").split(";")[0].toLowerCase(Locale.ROOT);
		String baseLang = element.findElements(By.className("baseLanguage")).get(0).getAttribute("innerText").split(";")[0].toLowerCase(Locale.ROOT);
		words.computeIfAbsent(targetLang.charAt(0), n -> new HashMap<>()).put(targetLang.replace(' ', '_'), baseLang); // ZoesteriaConfig doesn't support space in keys
	}

	private static String readString(FallableIOSupplier<InputStream> isSupplier) throws IOException {
		InputStream is = isSupplier.get();
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();

		int nBytesRead;
		byte[] bufferBuffer = new byte[0x4000];

		while ((nBytesRead = is.read(bufferBuffer, 0, bufferBuffer.length)) != -1) {
			buffer.write(bufferBuffer, 0, nBytesRead);
		}

		is.close();
		return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
	}

	private static final By LANG_ENTRY = By.className("preview-grid-item-content");
	private static final By START_BUTTON = By.id("start-button-main");
	private static final By FULL_LIST_SWITCH = By.xpath("//*[@id=\"full-list-switcher\"]");
	private static final By TEXT = By.xpath("//*[@id=\"question-text\"]/span");
	private static final By INPUT = By.xpath("/html/body/div[1]/div[2]/div/ui-view/div[1]/div[2]/div/div/div[2]/div[2]/game-lp-answer-input/div/div[2]/input");
	private static final By SKIP = By.xpath("/html/body/div[1]/div[2]/div[1]/div/div/div[2]/button");

	//private static final By SCROLLBAR = By.xpath("//*[@id=\"preview-grid-container\"]/div[2]");
}

interface FallableIOSupplier<T> {
	T get() throws IOException;
}
