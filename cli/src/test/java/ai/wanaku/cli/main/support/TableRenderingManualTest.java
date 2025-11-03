package ai.wanaku.cli.main.support;

import org.jline.utils.AttributedString;

/**
 * Manual test to visualize table rendering.
 * Run this test and check the console output to verify table formatting.
 */
public class TableRenderingManualTest {

    public static void main(String[] args) {
        String markdown =
                """
                ### Comparison Operators

                | Operator | Description | Example |
                |----------|-------------|---------|
                | `=` | Equals | `category=weather` |
                | `!=` | Not equals | `status!=deprecated` |

                ### Logical Operators

                | Operator | Description | Example |
                |----------|-------------|---------|
                | `&` | Logical AND | `category=weather & version=2.0` |
                | `|` | Logical OR | `category=weather \\| category=news` |
                | `!` | Logical NOT | `!deprecated=true` |
                | `( )` | Grouping | `(a=1 \\| b=2) & c=3` |
                """;

        System.out.println("=== Table Rendering Test ===\n");

        AttributedString result = MarkdownRenderer.render(markdown);
        System.out.println(result.toAnsi());

        System.out.println("\n=== End of Test ===");
    }
}
