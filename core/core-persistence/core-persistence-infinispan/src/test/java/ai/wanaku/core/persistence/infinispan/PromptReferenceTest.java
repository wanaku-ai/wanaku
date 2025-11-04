package ai.wanaku.core.persistence.infinispan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import ai.wanaku.api.types.PromptMessage;
import ai.wanaku.api.types.PromptReference;
import ai.wanaku.api.types.PromptReference.PromptArgument;
import ai.wanaku.api.types.TextContent;
import ai.wanaku.core.persistence.api.PromptReferenceRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PromptReferenceTest {

    @Inject
    PromptReferenceRepository promptReferenceRepository;

    @Test
    @Order(1)
    public void insertThree() {
        for (int i = 1; i < 4; i++) {
            PromptReference promptReference = new PromptReference();
            promptReference.setId("id" + i);
            promptReference.setName("name" + i);
            promptReference.setDescription("description" + i);
            promptReference.setNamespace("namespace" + i);

            // Create prompt messages
            PromptMessage userMessage = new PromptMessage();
            userMessage.setRole("user");
            userMessage.setContent(new TextContent("User message " + i));

            PromptMessage assistantMessage = new PromptMessage();
            assistantMessage.setRole("assistant");
            assistantMessage.setContent(new TextContent("Assistant message " + i));

            promptReference.setMessages(List.of(userMessage, assistantMessage));

            // Create prompt arguments
            PromptArgument argument = new PromptArgument();
            argument.setName("arg" + i);
            argument.setDescription("Argument description " + i);
            argument.setRequired(i % 2 == 0);

            promptReference.setArguments(List.of(argument));

            // Set tool references
            promptReference.setToolReferences(List.of("tool" + i));

            promptReferenceRepository.persist(promptReference);
        }
    }

    @Test
    @Order(2)
    public void list() {
        List<PromptReference> entities = promptReferenceRepository.listAll();

        assertEquals(3, entities.size());
        final Optional<PromptReference> refOpt =
                entities.stream().filter(entity -> entity.getId().equals("id1")).findFirst();

        Assertions.assertTrue(refOpt.isPresent());
        PromptReference name1Entity = refOpt.get();
        assertEquals("name1", name1Entity.getName());
        assertEquals("description1", name1Entity.getDescription());
        assertEquals("namespace1", name1Entity.getNamespace());
        assertNotNull(name1Entity.getMessages());
        assertEquals(2, name1Entity.getMessages().size());
        assertNotNull(name1Entity.getArguments());
        assertEquals(1, name1Entity.getArguments().size());
        assertEquals("arg1", name1Entity.getArguments().get(0).getName());
        assertNotNull(name1Entity.getToolReferences());
        assertEquals(1, name1Entity.getToolReferences().size());
        assertEquals("tool1", name1Entity.getToolReferences().get(0));
    }

    @Test
    @Order(3)
    public void find() {
        PromptReference model = promptReferenceRepository.findById("id1");

        Assertions.assertNotNull(model);
        assertEquals("name1", model.getName());
        assertNotNull(model.getMessages());
        assertEquals(2, model.getMessages().size());
        assertEquals("user", model.getMessages().get(0).getRole());
        assertEquals("assistant", model.getMessages().get(1).getRole());
    }

    @Test
    @Order(4)
    public void findByName() {
        List<PromptReference> prompts = promptReferenceRepository.findByName("name1");

        Assertions.assertNotNull(prompts);
        assertEquals(1, prompts.size());
        assertEquals("id1", prompts.get(0).getId());
    }

    @Test
    @Order(5)
    public void findNonExistent() {
        Assertions.assertNull(promptReferenceRepository.findById("nameNotExists"));
    }

    @Test
    @Order(6)
    public void delete() {
        int initialSize = promptReferenceRepository.listAll().size();

        promptReferenceRepository.deleteById("id2");

        int finalSize = promptReferenceRepository.listAll().size();

        Assertions.assertTrue(finalSize < initialSize);
        assertEquals(2, finalSize);
    }
}
