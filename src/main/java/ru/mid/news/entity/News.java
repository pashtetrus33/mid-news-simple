package ru.mid.news.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class News implements Comparable<News> {

    private String url;

    private String title;

    private String content;

    private LocalDateTime publicationDate;

    @Override
    public int compareTo(News other) {
        return this.publicationDate.compareTo(other.publicationDate);
    }
}