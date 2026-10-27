package com.example.backend;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.common.NotFoundException;

@RestController
@RequestMapping("/api/_test")
public class TestController {

    @GetMapping("/notfound")
    public void notFound() {
        throw new NotFoundException("Test: resource not found");
    }

    @GetMapping("/boom")
    public void boom() {
        throw new RuntimeException("Test: unexpected error");
    }
}
