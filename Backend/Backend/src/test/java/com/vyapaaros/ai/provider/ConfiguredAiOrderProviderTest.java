package com.vyapaaros.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ConfiguredAiOrderProviderTest {

    @Test
    void configuredModelIsUsedWithoutGuessingOrRemapping() {
        assertEquals("gemini-2.5-flash", ConfiguredAiOrderProvider.resolveModel(" gemini-2.5-flash "));
        assertEquals("gemini-3.8-flash", ConfiguredAiOrderProvider.resolveModel("gemini-3.8-flash"));
        assertEquals("gemini-3.5-flash", ConfiguredAiOrderProvider.resolveModel("gemini-3.5-flash"));
        assertEquals("", ConfiguredAiOrderProvider.resolveModel(null));
    }

    @Test
    public void testExtractThrowsWhenNotConfigured() {
        // Create provider with empty configuration values
        ConfiguredAiOrderProvider provider = new ConfiguredAiOrderProvider(
                new ObjectMapper(),
                "", // endpoint
                "", // apiKey
                "", // model
                1000,
                null
        );
        // Expect AiProviderException due to missing configuration
        AiProviderException exception = assertThrows(AiProviderException.class, () -> {
            provider.extract("some text");
        });
        assertTrue(exception.getMessage().contains("VYAPAAROS_AI_API_KEY"));
    }
}
