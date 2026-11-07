package com.riansares.r4r.config;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MVC CORS and registered security filter, without a database or LLM. */
class RagApiCorsTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        // Load the production allowlist, not a test-only copy of its origins.
        for (var source : new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))) {
            context.getEnvironment().getPropertySources().addFirst(source);
        }
        context.register(ProbeConfiguration.class, WebConfiguration.class);
        context.refresh();
        var registration = context.getBean("ragApiSecurityFilter", FilterRegistrationBean.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters((Filter) registration.getFilter()).build();
    }

    @AfterEach
    void tearDown() {
        if (context != null) context.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:8000", "http://127.0.0.1:8000",
            "http://localhost:63342", "http://127.0.0.1:63342", "https://athagon.tech"
    })
    void permitsGetPostAndPreflightButRetainsRateLimit(String origin) throws Exception {
        mvc.perform(get("/api/rag/runtime").header("Origin", origin))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", origin));
        mvc.perform(options("/api/rag/runtime").header("Origin", origin)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", origin));
        mvc.perform(options("/api/rag/answers").header("Origin", origin)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", origin))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,OPTIONS"))
                .andExpect(header().string("Access-Control-Allow-Headers", "content-type"));
        mvc.perform(options("/api/rag/warmup").header("Origin", origin).header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", origin));
        // Warmups do not consume answer quotas; their cooldown is per model in the service.
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/rag/warmup").header("Origin", origin))
                .andExpect(status().isAccepted()).andExpect(header().string("Access-Control-Allow-Origin", origin));
        mvc.perform(post("/api/rag/answers").header("Origin", origin)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", origin));
        mvc.perform(post("/api/rag/answers").header("Origin", origin)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Access-Control-Allow-Origin", origin))
                .andExpect(jsonPath("$.error").value("rate_limited"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://evil.example", "http://localhost:8001", "https://localhost:8000",
            "http://localhost:8000.evil.example", "https://athagon.tech.evil.example", "null" })
    void rejectsUnauthorizedOriginsForGetPostAndPreflight(String origin) throws Exception {
        mvc.perform(post("/api/rag/warmup").header("Origin", origin))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(options("/api/rag/warmup").header("Origin", origin).header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/rag/runtime").header("Origin", origin))
                .andExpect(status().isForbidden()).andExpect(content().string("Invalid CORS request"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(post("/api/rag/answers").header("Origin", origin)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("origin_not_allowed"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        for (String method : new String[] { "GET", "POST" }) {
            mvc.perform(options(method.equals("GET") ? "/api/rag/runtime" : "/api/rag/answers")
                            .header("Origin", origin).header("Access-Control-Request-Method", method)
                            .header("Access-Control-Request-Headers", "content-type"))
                    .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }
    }

    @Test
    void rejectsUnapprovedMethodsAndPostWithoutOrigin() throws Exception {
        mvc.perform(options("/api/rag/answers").header("Origin", "http://localhost:8000")
                        .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isForbidden());
        mvc.perform(options("/api/rag/answers").header("Origin", "http://localhost:8000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "x-unapproved"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/rag/warmup"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("origin_required"));
        mvc.perform(post("/api/rag/answers").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("origin_required"));
    }

    @Configuration
    @EnableWebMvc
    @Import(ProbeController.class)
    static class ProbeConfiguration { }

    @RestController
    static class ProbeController {
        @GetMapping("/api/rag/runtime")
        String runtime() { return "{}"; }

        @PostMapping("/api/rag/warmup")
        org.springframework.http.ResponseEntity<String> warmup() { return org.springframework.http.ResponseEntity.accepted().body("{}"); }

        @PostMapping("/api/rag/answers")
        String answer() { return "{}"; }
    }
}
