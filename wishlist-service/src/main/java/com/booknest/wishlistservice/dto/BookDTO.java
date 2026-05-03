package com.booknest.wishlistservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookDTO {
    private Long bookId;
    private String title;
    private String author;
    private String genre;
    private Double price;
    private Integer stock;
    private String description;
    private String coverImageUrl;
}
