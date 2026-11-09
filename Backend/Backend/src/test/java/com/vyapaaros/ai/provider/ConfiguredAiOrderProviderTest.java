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

    @Test
    void sourceTextControlsUnitAndUnstatedUnitsRemainAmbiguous() throws Exception {
        ConfiguredAiOrderProvider provider = new ConfiguredAiOrderProvider(
            new ObjectMapper(), "", "", "", 1000, null
        );
        ObjectMapper mapper = new ObjectMapper();
        String generated = mapper.writeValueAsString(java.util.Map.of("items", java.util.List.of(
            java.util.Map.of("originalText", "5 peti biscuit", "productDescription", "biscuit", "paidCases", 5, "unit", "PIECE", "confidence", 0.9),
            java.util.Map.of("originalText", "2 biscuit", "productDescription", "biscuit", "paidCases", 2, "unit", "CASE", "unitAmbiguous", false, "confidence", 0.8),
            java.util.Map.of("originalText", "1 piece tea", "productDescription", "tea", "paidCases", 1, "unit", "CASE", "confidence", 0.8)
        )));
        String response = mapper.writeValueAsString(java.util.Map.of("candidates", java.util.List.of(
            java.util.Map.of("content", java.util.Map.of("parts", java.util.List.of(java.util.Map.of("text", generated))))
        )));
        var items = provider.parseItems(response);

        assertEquals("CASE", items.get(0).unit());
        assertEquals(false, items.get(0).unitAmbiguous());
        assertEquals("CASE", items.get(1).unit());
        assertEquals(true, items.get(1).unitAmbiguous());
        assertEquals("PIECE", items.get(2).unit());
        assertEquals(false, items.get(2).unitAmbiguous());
    }
}
