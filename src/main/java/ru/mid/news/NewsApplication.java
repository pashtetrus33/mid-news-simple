package ru.mid.news;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import ru.mid.news.service.WebScraperService;

import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@RequiredArgsConstructor
public class NewsApplication implements CommandLineRunner {

    private final WebScraperService webScraperService;

    public static void main(String[] args) {

        ConfigurableApplicationContext context = SpringApplication.run(NewsApplication.class, args);
        System.exit(SpringApplication.exit(context)); // Завершение приложения
    }

    @Override
    public void run(String... args) throws Exception {
        webScraperService.scrapeData(); // Запуск метода scrapeData()
        System.exit(0); // Завершение приложения после выполнения
    }
}