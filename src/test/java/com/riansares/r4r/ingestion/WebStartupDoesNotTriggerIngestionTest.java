package com.riansares.r4r.ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class WebStartupDoesNotTriggerIngestionTest {

    @MockBean
    private KnowledgeIngestionService mockIngestionService;

    @Autowired
    private org.springframework.context.ApplicationContext context;

    @Test
    void normalApplicationStartupDoesNotTriggerIngestion() {
        // Retrieve the bean from the SpringBootTest context and assert identity with registered mock
        var ingesterBean = context.getBean(KnowledgeIngestionService.class);
        assertThat(ingesterBean).isSameAs(mockIngestionService)
                .as("The injection target should be the @MockBean-registered mock");

        // Assert no interactions with the mock (context is still active for Spring Boot test purposes)
        verifyNoInteractions(mockIngestionService);
    }
}
