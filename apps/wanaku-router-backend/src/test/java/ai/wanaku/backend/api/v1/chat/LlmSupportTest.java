package ai.wanaku.backend.api.v1.chat;

import ai.wanaku.backend.support.NoOidcTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import static ai.wanaku.backend.api.v1.chat.LlmSupport.ANTHROPIC;
import static ai.wanaku.backend.api.v1.chat.LlmSupport.GEMINI;
import static ai.wanaku.backend.api.v1.chat.LlmSupport.MISTRAL;
import static ai.wanaku.backend.api.v1.chat.LlmSupport.OLLAMA;
import static ai.wanaku.backend.api.v1.chat.LlmSupport.OPEN_AI;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(NoOidcTestProfile.class)
public class LlmSupportTest {

    @Inject
    LlmSupport llmSupport;

    @Test
    public void testAnthropicIsSupported() {
        assertTrue(llmSupport.getSupportedLlms().contains(ANTHROPIC));
    }

    @Test
    public void testSuggestedAnthropicModels() {
        assertFalse(llmSupport.getModelSuggestions(ANTHROPIC).isEmpty());
    }

    @Test
    public void testCreateAnthropicChatModel() {
        ChatModel claude = llmSupport.createChatModel(ANTHROPIC, "claude-opus-4-6", "<api_key>");
        assertInstanceOf(AnthropicChatModel.class, claude);
    }

    @Test
    public void testGeminiIsSupported() {
        assertTrue(llmSupport.getSupportedLlms().contains(GEMINI));
    }

    @Test
    public void testSuggestedGeminiModels() {
        assertFalse(llmSupport.getModelSuggestions(GEMINI).isEmpty());
    }

    @Test
    public void testCreateGeminiChatModel() {
        ChatModel gemini = llmSupport.createChatModel(GEMINI, "gemini-2.5-flash-lite", "<api_key>");
        assertInstanceOf(GoogleAiGeminiChatModel.class, gemini);
    }

    @Test
    public void testMistralIsSupported() {
        assertTrue(llmSupport.getSupportedLlms().contains(MISTRAL));
    }

    @Test
    public void testSuggestedMistralModels() {
        assertFalse(llmSupport.getModelSuggestions(MISTRAL).isEmpty());
    }

    @Test
    public void testCreateMistralChatModel() {
        ChatModel mistral = llmSupport.createChatModel(MISTRAL, "mistral-small-latest", "<api_key>");
        assertInstanceOf(MistralAiChatModel.class, mistral);
    }

    @Test
    public void testOllamaIsSupported() {
        assertTrue(llmSupport.getSupportedLlms().contains(OLLAMA));
    }

    @Test
    public void testCreateOllamaChatModel() {
        ChatModel ollama = llmSupport.createChatModel(OLLAMA, "llama-4", null);
        assertInstanceOf(OllamaChatModel.class, ollama);
    }

    @Test
    public void testOpenAiIsSupported() {
        assertTrue(llmSupport.getSupportedLlms().contains(OPEN_AI));
    }

    @Test
    public void testSuggestedOpenAiModels() {
        assertFalse(llmSupport.getModelSuggestions(OPEN_AI).isEmpty());
    }

    @Test
    public void testCreateOpenAiChatModel() {
        ChatModel gpt = llmSupport.createChatModel(OPEN_AI, "gpt-4", "<api_key>");
        assertInstanceOf(OpenAiChatModel.class, gpt);
    }

    @Test
    public void testUnsupportedLlmSuggestedModels() {
        assertThrows(IllegalArgumentException.class, () -> llmSupport.getModelSuggestions("FooBar"));
    }

    @Test
    public void testCreateUnsupportedModel() {
        assertThrows(IllegalArgumentException.class, () -> llmSupport.createChatModel("FooBar", "foobar", "<api_key>"));
    }
}
