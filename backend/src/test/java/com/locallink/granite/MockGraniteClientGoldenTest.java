package com.locallink.granite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locallink.seed.SeedStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden decomposition test.
 *
 * For every need in seed/golden_needs.json:
 *   - Every expected_core skill must appear in the MockGraniteClient decomposition.
 *   - No should_not_include skill may appear.
 *
 * This runs in a full Spring Boot context so that the SeedStore is populated
 * from seed/skills_taxonomy.json and seed/keyword_fallback.json is on the classpath.
 *
 * See docs/PROJECT_BRIEF.md §Success criteria and docs/GRANITE_PROMPTS.md §Golden check.
 */
@SpringBootTest
class MockGraniteClientGoldenTest {

    @Autowired
    private SeedStore store;

    @Autowired
    private ObjectMapper mapper;

    private MockGraniteClient client;

    @BeforeEach
    void setUp() {
        client = new MockGraniteClient(store, mapper);
    }

    @Test
    void allGoldenNeedsDecomposeCorrectly() throws Exception {
        // Load golden_needs.json from the classpath (copied there by maven-resources-plugin)
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("seed/golden_needs.json")) {
            assertThat(is).as("seed/golden_needs.json must be on classpath").isNotNull();
            JsonNode root = mapper.readTree(is);

            for (JsonNode need : root.get("needs")) {
                String id = need.get("id").asText();
                String text = need.get("text").asText();
                String city = need.get("city").asText();

                List<String> expectedCore = new ArrayList<>();
                if (need.has("expected_core")) {
                    for (JsonNode s : need.get("expected_core")) expectedCore.add(s.asText());
                }
                List<String> shouldNotInclude = new ArrayList<>();
                if (need.has("should_not_include")) {
                    for (JsonNode s : need.get("should_not_include")) shouldNotInclude.add(s.asText());
                }

                DecomposeResponse response = client.decompose(new DecomposeRequest(text, city));
                List<String> returnedSkills = response.skills().stream()
                        .map(DecomposeResponse.SkillMatch::skillId)
                        .toList();

                // Every expected_core skill must be present
                for (String core : expectedCore) {
                    assertThat(returnedSkills)
                            .as("Need %s: expected_core skill '%s' missing from decomposition %s",
                                    id, core, returnedSkills)
                            .contains(core);
                }

                // No should_not_include skill may appear
                for (String excluded : shouldNotInclude) {
                    assertThat(returnedSkills)
                            .as("Need %s: should_not_include skill '%s' appeared in decomposition %s",
                                    id, excluded, returnedSkills)
                            .doesNotContain(excluded);
                }
            }
        }
    }
}
