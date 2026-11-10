package com.riansares.r4r.vector.config;

import com.riansares.r4r.vector.titan.TitanLambdaKnowledgeStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.lambda.LambdaClient;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TitanRetrievalConfigurationTest {
    final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(TitanRetrievalConfiguration.class);
    @Test void defaultDoesNotCreateAwsClientOrResolveCredentials() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(LambdaClient.class).doesNotHaveBean(TitanLambdaKnowledgeStore.class));
    }
    @Test void explicitEnableUsesExistingClientAndOneQualifiedStore() {
        var client = mock(LambdaClient.class);
        runner.withPropertyValues("r4r.rag.vector.titan.enabled=true", "r4r.rag.vector.titan.minimum-similarity=0.6")
                .withBean("titanLambdaClient", LambdaClient.class, () -> client).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(TitanLambdaKnowledgeStore.class);
                    verifyNoInteractions(client);
                });
    }
    @ParameterizedTest @ValueSource(strings = {"minimum-similarity=", "minimum-similarity=NaN", "minimum-similarity=1.2",
            "timeout=0s", "timeout=31s", "max-top-k=21", "function-name=other", "region=eu-west-1"})
    void invalidEnabledConfigFailsBeforeAws(String property) {
        runner.withPropertyValues("r4r.rag.vector.titan.enabled=true", "r4r.rag.vector.titan.minimum-similarity=0.6",
                "r4r.rag.vector.titan." + property).run(context -> assertThat(context).hasFailed());
    }
}
