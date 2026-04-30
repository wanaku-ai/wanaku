package ai.wanaku.cli.main.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;

public class MarkdownRenderer {

    private static final Style HEADING_STYLE = Style.EMPTY.fg(Color.CYAN).bold();
    private static final Style BOLD_STYLE = Style.EMPTY.bold();
    private static final Style ITALIC_STYLE = Style.EMPTY.italic();
    private static final Style CODE_STYLE = Style.EMPTY.fg(Color.YELLOW).bg(Color.BLACK);
    private static final Style LINK_STYLE = Style.EMPTY.fg(Color.BLUE).underlined();

    public static dev.tamboui.text.Text render(String markdown) {
        if (markdown == null) {
            throw new IllegalArgumentException("Markdown text cannot be null");
        }

        Parser parser = Parser.builder()
                .extensions(Arrays.asList(TablesExtension.create()))
                .build();
        Node document = parser.parse(markdown);

        MarkdownVisitor visitor = new MarkdownVisitor();
        document.accept(visitor);

        return visitor.getResult();
    }

    private static class MarkdownVisitor extends AbstractVisitor {
        private final List<Line> lines = new ArrayList<>();
        private List<Span> currentLineSpans = new ArrayList<>();
        private Style currentStyle = Style.EMPTY;
        private int listDepth = 0;
        private List<List<String>> tableRows = new ArrayList<>();
        private List<String> currentRow = new ArrayList<>();
        private StringBuilder currentCell = new StringBuilder();
        private boolean inTable = false;

        @Override
        public void visit(CustomBlock customBlock) {
            if (customBlock instanceof TableBlock) {
                visit((TableBlock) customBlock);
            } else {
                super.visit(customBlock);
            }
        }

        @Override
        public void visit(CustomNode customNode) {
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
            flushLine();
            currentStyle = HEADING_STYLE;
            String prefix = "#".repeat(heading.getLevel()) + " ";
            currentLineSpans.add(Span.styled(prefix, HEADING_STYLE));
            visitChildren(heading);
            currentStyle = Style.EMPTY;
            flushLine();
            flushLine();
        }

        @Override
        public void visit(Paragraph paragraph) {
            visitChildren(paragraph);
            flushLine();
            flushLine();
        }

        @Override
        public void visit(Text text) {
            if (inTable) {
                currentCell.append(text.getLiteral());
            } else {
                currentLineSpans.add(Span.styled(text.getLiteral(), currentStyle));
            }
        }

        @Override
        public void visit(Emphasis emphasis) {
            if (!inTable) {
                Style previousStyle = currentStyle;
                currentStyle = ITALIC_STYLE;
                visitChildren(emphasis);
                currentStyle = previousStyle;
            } else {
                visitChildren(emphasis);
            }
        }

        @Override
        public void visit(StrongEmphasis strong) {
            if (!inTable) {
                Style previousStyle = currentStyle;
                currentStyle = BOLD_STYLE;
                visitChildren(strong);
                currentStyle = previousStyle;
            } else {
                visitChildren(strong);
            }
        }

        @Override
        public void visit(Code code) {
            if (inTable) {
                currentCell.append(code.getLiteral());
            } else {
                currentLineSpans.add(Span.styled(code.getLiteral(), CODE_STYLE));
            }
        }

        @Override
        public void visit(FencedCodeBlock codeBlock) {
            flushLine();
            for (String codeLine : codeBlock.getLiteral().split("\n", -1)) {
                currentLineSpans.add(Span.styled(codeLine, CODE_STYLE));
                flushLine();
            }
        }

        @Override
        public void visit(IndentedCodeBlock codeBlock) {
            flushLine();
            for (String codeLine : codeBlock.getLiteral().split("\n", -1)) {
                currentLineSpans.add(Span.styled(codeLine, CODE_STYLE));
                flushLine();
            }
        }

        @Override
        public void visit(BulletList bulletList) {
            listDepth++;
            visitChildren(bulletList);
            listDepth--;
            flushLine();
        }

        @Override
        public void visit(OrderedList orderedList) {
            listDepth++;
            visitChildren(orderedList);
            listDepth--;
            flushLine();
        }

        @Override
        public void visit(ListItem listItem) {
            String indent = "  ".repeat(listDepth - 1);
            currentLineSpans.add(Span.raw(indent + "• "));
            visitChildren(listItem);
        }

        @Override
        public void visit(Link link) {
            Style previousStyle = currentStyle;
            currentStyle = LINK_STYLE;
            visitChildren(link);
            currentStyle = previousStyle;

            if (link.getDestination() != null && !link.getDestination().isEmpty()) {
                currentLineSpans.add(Span.raw(" (" + link.getDestination() + ")"));
            }
        }

        @Override
        public void visit(HardLineBreak hardLineBreak) {
            flushLine();
        }

        @Override
        public void visit(SoftLineBreak softLineBreak) {
            currentLineSpans.add(Span.raw(" "));
        }

        public void visit(TableBlock tableBlock) {
            inTable = true;
            tableRows = new ArrayList<>();
            visitChildren(tableBlock);
            renderTable();
            inTable = false;
        }

        public void visit(TableHead tableHead) {
            visitChildren(tableHead);
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

            int numColumns = tableRows.get(0).size();
            int[] columnWidths = new int[numColumns];

            for (List<String> row : tableRows) {
                for (int i = 0; i < row.size() && i < numColumns; i++) {
                    columnWidths[i] = Math.max(columnWidths[i], row.get(i).length());
                }
            }

            flushLine();

            renderBorder(columnWidths, "┌", "┬", "┐");

            if (!tableRows.isEmpty()) {
                renderRow(tableRows.get(0), columnWidths, true);
                renderBorder(columnWidths, "├", "┼", "┤");

                for (int i = 1; i < tableRows.size(); i++) {
                    renderRow(tableRows.get(i), columnWidths, false);
                }
            }

            renderBorder(columnWidths, "└", "┴", "┘");
            flushLine();
            tableRows.clear();
        }

        private void renderBorder(int[] columnWidths, String left, String middle, String right) {
            StringBuilder sb = new StringBuilder();
            sb.append(left);
            for (int i = 0; i < columnWidths.length; i++) {
                sb.append("─".repeat(columnWidths[i] + 2));
                if (i < columnWidths.length - 1) {
                    sb.append(middle);
                }
            }
            sb.append(right);
            currentLineSpans.add(Span.raw(sb.toString()));
            flushLine();
        }

        private void renderRow(List<String> row, int[] columnWidths, boolean isHeader) {
            List<Span> spans = new ArrayList<>();
            spans.add(Span.raw("│"));
            for (int i = 0; i < columnWidths.length; i++) {
                String cell = i < row.size() ? row.get(i) : "";
                String padded = " " + cell + " ".repeat(columnWidths[i] - cell.length()) + " ";

                if (isHeader) {
                    spans.add(Span.styled(padded, BOLD_STYLE));
                } else {
                    spans.add(Span.raw(padded));
                }
                spans.add(Span.raw("│"));
            }
            lines.add(Line.from(spans));
        }

        private void flushLine() {
            if (!currentLineSpans.isEmpty()) {
                lines.add(Line.from(new ArrayList<>(currentLineSpans)));
                currentLineSpans.clear();
            } else {
                lines.add(Line.empty());
            }
        }

        public dev.tamboui.text.Text getResult() {
            if (!currentLineSpans.isEmpty()) {
                flushLine();
            }
            return dev.tamboui.text.Text.from(lines);
        }
    }
}
