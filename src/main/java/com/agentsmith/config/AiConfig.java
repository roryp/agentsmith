package com.agentsmith.config;

import com.azure.core.http.okhttp.OkHttpAsyncClientProvider;
import com.azure.identity.DefaultAzureCredentialBuilder;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Value("${azure.ai.endpoint}")
    private String endpoint;

    @Value("${azure.ai.deployment}")
    private String deploymentName;

    @Bean
    public ChatModel chatModel() {
        return AzureOpenAiChatModel.builder()
                .endpoint(endpoint)
                .tokenCredential(new DefaultAzureCredentialBuilder().build())
                .deploymentName(deploymentName)
                .httpClientProvider(new OkHttpAsyncClientProvider())
                .maxCompletionTokens(2000)
                .build();
    }
}
