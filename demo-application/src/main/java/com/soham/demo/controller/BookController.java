package com.soham.demo.controller;

import com.soham.ratelimiter.algorithm.Algorithm;
import com.soham.ratelimiter.annotation.RateLimit;
import com.soham.demo.model.Book;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Demonstrates the entire library's end-user experience: add
 * {@code rate-limiter-spring-boot-starter} as a dependency, annotate a
 * handler method with {@code @RateLimit}, and nothing else — no manual bean
 * wiring, no manual Redis client setup.
 */
@RestController
@RequestMapping("/books")
public class BookController {

    private static final List<Book> BOOKS = List.of(
            new Book("1", "Clean Code", "Robert C. Martin"),
            new Book("2", "Effective Java", "Joshua Bloch"),
            new Book("3", "Designing Data-Intensive Applications", "Martin Kleppmann")
    );

    @RateLimit(limit = 5, windowSeconds = 60, algorithm = Algorithm.FIXED_WINDOW)
    @GetMapping
    public List<Book> getBooks() {
        return BOOKS;
    }

    @RateLimit(limit = 3, windowSeconds = 30, algorithm = Algorithm.TOKEN_BUCKET)
    @GetMapping("/burstable")
    public List<Book> getBooksBurstable() {
        return BOOKS;
    }
}
