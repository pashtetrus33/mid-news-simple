package ru.mid.news.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


@Slf4j
@Service
public class WebScraperService {

    private boolean incognitoMode;
    private int minDelay;
    private int maxDelay;
    private String pageContentDivClass;
    private WebDriver driver;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private final List<LocalDateTime> existingNewsDates = new ArrayList<>();
    private final Path baseDirectory = Paths.get("mid-news");

    @PostConstruct
    public void init() {
        setupWebDriver();
        loadExistingNewsDates();
    }

    private void loadExistingNewsDates() {
        try {
            Files.walk(baseDirectory)
                    .filter(Files::isRegularFile) // Искать только файлы
                    .filter(file -> file.getFileName().toString().equals("index.html")) // Фильтр по имени файла
                    .forEach(file -> {
                        List<String> lines;
                        try {
                            lines = Files.readAllLines(file);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        for (String line : lines) {
                            if (line.contains(" - ")) {
                                String cleanLine = line.replaceAll("<.*?>", "").trim();
                                String datePart = cleanLine.substring(0, cleanLine.indexOf(" - ")).trim();
                                try {
                                    LocalDateTime publicationDate = LocalDateTime.parse(datePart, formatter);
                                    existingNewsDates.add(publicationDate);
                                } catch (DateTimeParseException e) {
                                    log.warn("Не удалось распарсить дату: {}", datePart);
                                }
                            }
                        }
                    });
        } catch (IOException e) {
            log.error("Ошибка при загрузке существующих новостей: ", e);
        }
    }


    private void setupWebDriver() {
        System.setProperty("webdriver.chrome.driver", "chrome/chromedriver.exe");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.82 Safari/537.36");
        options.addArguments("--disable-blink-features=AutomationControlled");

        if (incognitoMode) {
            options.addArguments("--incognito");
        }

        driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
    }

    public void scrapeData() {

        Properties props = new Properties();
        try (FileInputStream input = new FileInputStream(Paths.get("config.txt").toFile())) {
            props.load(input);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }


        String url = props.getProperty("main.page");
        incognitoMode = Boolean.parseBoolean(props.getProperty("scraper.incognito"));
        pageContentDivClass = props.getProperty("div.page-content");
        String announceItemClass = props.getProperty("div.announce-item");
        minDelay = Integer.parseInt(props.getProperty("random-delay.min"));
        maxDelay = Integer.parseInt(props.getProperty("random-delay.max"));


        try {
            driver.manage().window().maximize();
            driver.get(url);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.className(pageContentDivClass)));

            WebElement contentDiv = driver.findElement(By.className(pageContentDivClass));
            List<WebElement> elements = contentDiv.findElements(By.className(announceItemClass));

            for (WebElement element : elements) {
                processElement(element);
            }
        } catch (Exception e) {
            log.error("Ошибка при сборе данных: ", e);
        } finally {
            cleanup();
        }
    }

    private void processElement(WebElement element) {
        String[] parts = element.getText().split("\n", 2);

        if (parts.length == 2) {
            LocalDateTime publicationDate = LocalDateTime.parse(parts[0].substring(0, 16).trim(), formatter);
            if (!existingNewsDates.contains(publicationDate)) {
                // Добавление новой новости
                String title = parts[1].trim();
                String link = element.findElement(By.tagName("a")).getAttribute("href");
                saveNews(publicationDate, title, link);
                existingNewsDates.add(publicationDate); // Обновляем кэш
            } else {
                log.info("Дата и время новости уже есть в локальном кэше! {}", publicationDate);
            }
        }
    }

    private void saveNews(LocalDateTime publicationDate, String title, String link) {
        try {

            int delay = ThreadLocalRandom.current().nextInt(minDelay, maxDelay);
            log.info("Ждем {} миллисекунд перед открытием страницы: {}", delay, link);
            Thread.sleep(delay);

            driver.get(link);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.className(pageContentDivClass)));

            WebElement contentDiv = driver.findElement(By.className(pageContentDivClass));
            String pageSource = contentDiv.getAttribute("outerHTML");

            String dateString = publicationDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            Path directoryPath = baseDirectory.resolve(dateString);

            if (!Files.exists(directoryPath)) {
                Files.createDirectory(directoryPath);
            }

            AtomicInteger count = new AtomicInteger(1);
            File directory = directoryPath.toFile();
            if (directory.exists() && directory.isDirectory()) {
                File[] files = directory.listFiles((dir, name) -> name.endsWith(".html"));
                if (files != null && files.length > 0) {
                    count.set(files.length + 1);
                }
            }

            String fileName = count.getAndIncrement() + ".html";
            Path filePath = directoryPath.resolve(fileName);

            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(pageSource);
                log.info("Страница сохранена в файл: {}", filePath);
            }

            updateIndexFile(directoryPath, publicationDate, title, fileName);
        } catch (Exception e) {
            log.warn("Ошибка при сохранении новости: {}", link, e);
        }
    }

    private void updateIndexFile(Path directoryPath, LocalDateTime publicationDate, String title, String fileName) {
        Path indexPath = directoryPath.resolve("index.html");
        String localFileLink = "<a href=\"" + fileName + "\">" + title + "</a>";
        String newEntry = "<p>" + publicationDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                + " - " + localFileLink + "</p>\n";

        try {
            if (!Files.exists(indexPath)) {
                try (FileWriter indexWriter = new FileWriter(indexPath.toFile())) {
                    indexWriter.write("<h1 style=\"font-weight: bold; text-align: left;\">Новости</h1>\n");
                    indexWriter.write(newEntry); // Добавляем первую запись
                }
            } else {
                prependToIndexFile(indexPath, newEntry);
            }

            log.info("Добавлена информация в index.html для новости: {}", title);
        } catch (IOException e) {
            log.warn("Ошибка при обновлении index.html для новости по ссылке: {}", title, e);
        }
    }

    private void prependToIndexFile(Path indexPath, String newEntry) throws IOException {
        List<String> lines = Files.readAllLines(indexPath);
        String header = lines.get(0);

        try (FileWriter writer = new FileWriter(indexPath.toFile())) {
            writer.write(header + "\n"); // Записываем заголовок
            writer.write(newEntry); // Записываем новую запись

            for (int i = 1; i < lines.size(); i++) {
                writer.write(lines.get(i) + "\n");
            }
        }
    }

    @PreDestroy
    public void cleanup() {
        if (driver != null) {
            driver.quit();
        }
    }
}