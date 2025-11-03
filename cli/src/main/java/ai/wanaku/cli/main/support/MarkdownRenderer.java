package ai.wanaku.cli.main.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.commonmark.ext.gfm.tables.*;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/**
 * Renders Markdown text with terminal styling using JLine3's AttributedString.
 *
 * <p>This class parses Markdown using the CommonMark library and converts it to
 * styled terminal output with ANSI color codes and text formatting.</p>
 *
 * <p>Supported Markdown features:</p>
 * <ul>
 *   <li>Headings (levels 1-6) - rendered in bold cyan</li>
 *   <li>Paragraphs - standard text with proper spacing</li>
 *   <li>Bold text (**text**) - rendered in bold</li>
 *   <li>Italic text (*text*) - rendered in italic (if terminal supports)</li>
 *   <li>Inline code (`code`) - rendered in yellow with background</li>
 *   <li>Bullet lists - rendered with bullet points</li>
 *   <li>Links - rendered in blue underlined with URL</li>
 *   <li>Code blocks - rendered in yellow</li>
 *   <li>Tables (GFM) - rendered with borders and proper alignment</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * String markdown = "# Hello World\n\nThis is **bold** text.";
 * AttributedString styled = MarkdownRenderer.render(markdown);
 * terminal.writer().println(styled.toAnsi());
 * }</pre>
 *
 */
public class MarkdownRenderer {

    // Color and style constants
    private static final AttributedStyle HEADING_STYLE = AttributedStyle.BOLD.foreground(AttributedStyle.CYAN);

    private static final AttributedStyle BOLD_STYLE = AttributedStyle.BOLD;

    private static final AttributedStyle ITALIC_STYLE = AttributedStyle.DEFAULT.italic();

    private static final AttributedStyle CODE_STYLE =
            AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW).background(AttributedStyle.BLACK);

    private static final AttributedStyle LINK_STYLE =
            AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE).underline();

    /**
     * Renders Markdown text to a styled AttributedString for terminal output.
     *
     * @param markdown the Markdown text to render
     * @return an AttributedString with terminal styling applied
     * @throws IllegalArgumentException if markdown is null
     */
    public static AttributedString render(String markdown) {
        if (markdown == null) {
            throw new IllegalArgumentException("Markdown text cannot be null");
        }

        // Build parser with GFM tables extension
        Parser parser = Parser.builder()
                .extensions(Arrays.asList(TablesExtension.create()))
                .build();
        Node document = parser.parse(markdown);

        MarkdownVisitor visitor = new MarkdownVisitor();
        document.accept(visitor);

        return visitor.getResult();
    }

    /**
     * Visitor that traverses the Markdown AST and builds styled output.
     */
    private static class MarkdownVisitor extends AbstractVisitor {
        private final AttributedStringBuilder builder = new AttributedStringBuilder();
        private int listDepth = 0;
        private List<List<String>> tableRows = new ArrayList<>();
        private List<String> currentRow = new ArrayList<>();
        private StringBuilder currentCell = new StringBuilder();
        private boolean inTableHeader = false;
        private boolean inTable = false;

        @Override
        public void visit(CustomBlock customBlock) {
            // Handle GFM table extension nodes - TableBlock extends CustomBlock
            if (customBlock instanceof TableBlock) {
                visit((TableBlock) customBlock);
            } else {
                super.visit(customBlock);
            }
        }

        @Override
        public void visit(CustomNode customNode) {
            // Handle GFM table extension nodes - TableHead, TableBody, TableRow, TableCell extend CustomNode
            if (customNode instanceof TableHead) {
                visit((TableHead) customNode);
            } else if (customNode instanceof TableBody) {
                visit((TableBody) customNode);
            } else if (customNode instanceof TableRow) {
                visit((TableRow) customNode);
            } else if (customNode instanceof TableCell) {
                visit((TableCell) customNode);
            } else {
                super.visit(customNode);
            }
        }

        @Override
        public void visit(Heading heading) {
            builder.style(HEADING_STYLE);

            // Add heading level indicator
            String prefix = "#".repeat(heading.getLevel()) + " ";
            builder.append(prefix);

            visitChildren(heading);
            builder.style(AttributedStyle.DEFAULT);
            builder.append("\n\n");
        }

        @Override
        public void visit(Paragraph paragraph) {
            visitChildren(paragraph);
            builder.append("\n\n");
        }

        @Override
        public void visit(Text text) {
            if (inTable) {
                // Inside a table, collect text for the current cell
                currentCell.append(text.getLiteral());
            } else {
                // Normal text rendering
                builder.append(text.getLiteral());
            }
        }

        @Override
        public void visit(Emphasis emphasis) {
            if (!inTable) {
                builder.style(ITALIC_STYLE);
            }
            visitChildren(emphasis);
            if (!inTable) {
                builder.style(AttributedStyle.DEFAULT);
            }
        }

        @Override
        public void visit(StrongEmphasis strong) {
            if (!inTable) {
                builder.style(BOLD_STYLE);
            }
            visitChildren(strong);
            if (!inTable) {
                builder.style(AttributedStyle.DEFAULT);
            }
        }

        @Override
        public void visit(Code code) {
            if (inTable) {
                // Inside a table, collect code text for the current cell
                currentCell.append(code.getLiteral());
            } else {
                // Normal code rendering with styling
                builder.style(CODE_STYLE);
                builder.append(code.getLiteral());
                builder.style(AttributedStyle.DEFAULT);
            }
        }

        @Override
        public void visit(FencedCodeBlock codeBlock) {
            builder.append("\n");
            builder.style(CODE_STYLE);
            builder.append(codeBlock.getLiteral());
            builder.style(AttributedStyle.DEFAULT);
            builder.append("\n");
        }

        @Override
        public void visit(IndentedCodeBlock codeBlock) {
            builder.append("\n");
            builder.style(CODE_STYLE);
            builder.append(codeBlock.getLiteral());
            builder.style(AttributedStyle.DEFAULT);
            builder.append("\n");
        }

        @Override
        public void visit(BulletList bulletList) {
            listDepth++;
            visitChildren(bulletList);
            listDepth--;
            builder.append("\n");
        }

        @Override
        public void visit(OrderedList orderedList) {
            listDepth++;
            visitChildren(orderedList);
            listDepth--;
            builder.append("\n");
        }

        @Override
        public void visit(ListItem listItem) {
            // Add indentation based on list depth
            builder.append("  ".repeat(listDepth - 1));
            builder.append("• ");
            visitChildren(listItem);
        }

        @Override
        public void visit(Link link) {
            builder.style(LINK_STYLE);
            visitChildren(link);
            builder.style(AttributedStyle.DEFAULT);

            // Show URL in parentheses
            if (link.getDestination() != null && !link.getDestination().isEmpty()) {
                builder.append(" (").append(link.getDestination()).append(")");
            }
        }

        @Override
        public void visit(HardLineBreak hardLineBreak) {
            builder.append("\n");
        }

        @Override
        public void visit(SoftLineBreak softLineBreak) {
            builder.append(" ");
        }

        public void visit(TableBlock tableBlock) {
            inTable = true;
            tableRows = new ArrayList<>();
            visitChildren(tableBlock);
            renderTable();
            inTable = false;
        }

        public void visit(TableHead tableHead) {
            inTableHeader = true;
            visitChildren(tableHead);
            inTableHeader = false;
        }

        public void visit(TableBody tableBody) {
            visitChildren(tableBody);
        }

        public void visit(TableRow tableRow) {
            currentRow = new ArrayList<>();
            visitChildren(tableRow);
            tableRows.add(currentRow);
        }

        public void visit(TableCell tableCell) {
            currentCell = new StringBuilder();
            visitChildren(tableCell);
            currentRow.add(currentCell.toString());
        }

        private void renderTable() {
            if (tableRows.isEmpty()) {
                return;
            }

            // Calculate column widths
            int numColumns = tableRows.get(0).size();
            int[] columnWidths = new int[numColumns];

            for (List<String> row : tableRows) {
                for (int i = 0; i < row.size() && i < numColumns; i++) {
                    columnWidths[i] = Math.max(columnWidths[i], row.get(i).length());
                }
            }

            // Render table with borders
            builder.append("\n");

            // Top border
            renderBorder(columnWidths, "┌", "┬", "┐");

            // Header row (first row)
            if (!tableRows.isEmpty()) {
                renderRow(tableRows.get(0), columnWidths, true);

                // Header separator
                renderBorder(columnWidths, "├", "┼", "┤");

                // Data rows
                for (int i = 1; i < tableRows.size(); i++) {
                    renderRow(tableRows.get(i), columnWidths, false);
                }
            }

            // Bottom border
            renderBorder(columnWidths, "└", "┴", "┘");

            builder.append("\n");
            tableRows.clear();
        }

        private void renderBorder(int[] columnWidths, String left, String middle, String right) {
            builder.append(left);
            for (int i = 0; i < columnWidths.length; i++) {
                builder.append("─".repeat(columnWidths[i] + 2)); // +2 for padding
                if (i < columnWidths.length - 1) {
                    builder.append(middle);
                }
            }
            builder.append(right);
            builder.append("\n");
        }

        private void renderRow(List<String> row, int[] columnWidths, boolean isHeader) {
            builder.append("│");
            for (int i = 0; i < columnWidths.length; i++) {
                String cell = i < row.size() ? row.get(i) : "";

                if (isHeader) {
                    builder.style(BOLD_STYLE);
                }

                builder.append(" ");
                builder.append(cell);
                // Pad to column width
                builder.append(" ".repeat(columnWidths[i] - cell.length()));
                builder.append(" ");

                if (isHeader) {
                    builder.style(AttributedStyle.DEFAULT);
                }

                builder.append("│");
            }
            builder.append("\n");
        }

        public AttributedString getResult() {
            return builder.toAttributedString();
        }
    }
}
