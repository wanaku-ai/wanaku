package ai.wanaku.cli.main.support;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tamboui.inline.InlineDisplay;
import dev.tamboui.layout.Constraint;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;

import static java.util.stream.Collectors.toMap;

public class WanakuPrinter {

    private static volatile boolean plainMode = false;

    public static void setPlainMode(boolean plain) {
        plainMode = plain;
    }

    private static final Style ERROR_STYLE = Style.EMPTY.fg(Color.RED).bold();
    private static final Style WARNING_STYLE = Style.EMPTY.fg(Color.YELLOW);
    private static final Style INFO_STYLE = Style.EMPTY.fg(Color.BLUE);
    private static final Style SUCCESS_STYLE = Style.EMPTY.fg(Color.GREEN).bold();

    private final Terminal terminal;
    private final ObjectMapper objectMapper;

    public WanakuPrinter(Terminal terminal) {
        this.terminal = validateNotNull(terminal, "Terminal cannot be null");
        this.objectMapper = new ObjectMapper();
    }

    public static Terminal terminalInstance() throws IOException {
        if (plainMode) {
            return newBuilder().jansi(false).jni(false).jna(false).color(false).build();
        }

        try {
            return newBuilder().jansi(true).color(true).jna(false).build();
        } catch (Exception e) {
            try {
                return newBuilder().jansi(false).jna(false).build();
            } catch (Exception ex) {
                return TerminalBuilder.builder().dumb(true).build();
            }
        }
    }

    private static TerminalBuilder newBuilder() {
        TerminalBuilder builder = TerminalBuilder.builder().system(!plainMode);
        if (plainMode) {
            builder.streams(System.in, System.out);
        }
        return builder;
    }

    public Terminal terminal() {
        return terminal;
    }

    public <T> void printTable(List<T> printables) {
        printTable(printables, new String[] {});
    }

    public <T> void printTable(List<T> printables, String... columns) {
        printTable(null, printables, columns);
    }

    public <T> void printTable(Map<String, Object> options, List<T> printables, String... columns) {
        printTable(options, printables, this::convertToMap, columns);
    }

    public <T> void printTable(
            Map<String, Object> options,
            List<T> objectsToPrint,
            Function<T, Map<String, Object>> toMap,
            String... columns) {
        if (objectsToPrint == null || objectsToPrint.isEmpty()) {
            return;
        }

        try {
            List<Map<String, Object>> mappedObjects =
                    objectsToPrint.stream().map(toMap).toList();

            List<String> columnNames;
            if (columns != null && columns.length > 0) {
                columnNames = Arrays.asList(columns);
            } else {
                columnNames = new ArrayList<>(mappedObjects.get(0).keySet());
            }

            Row header = Row.from(columnNames.stream().map(Cell::from).toList());

            List<Row> rows = mappedObjects.stream()
                    .map(map -> Row.from(columnNames.stream()
                            .map(col -> Cell.from(String.valueOf(map.getOrDefault(col, ""))))
                            .toList()))
                    .toList();

            Constraint[] widths =
                    columnNames.stream().map(col -> Constraint.fill()).toArray(Constraint[]::new);

            Table table = Table.builder()
                    .header(header)
                    .rows(rows)
                    .widths(widths)
                    .columnSpacing(1)
                    .build();

            int lineCount = rows.size() + 3;

            try (InlineDisplay display = InlineDisplay.create(lineCount)) {
                display.render((area, buffer) -> {
                    TableState state = new TableState();
                    table.render(area, buffer, state);
                });
            }
        } catch (Exception e) {
            printErrorMessage("Failed to print table: " + e.getMessage());
        }
    }

    public <T> void printAsMap(T object) {
        printAsMap(object, new String[] {});
    }

    public <T> void printAsMap(T object, String... keys) {
        if (object == null) {
            return;
        }

        Map<String, Object> map = convertToMap(object);
        if (keys != null && keys.length > 0) {
            Set<String> keySet = Set.of(keys);
            map = map.entrySet().stream()
                    .filter(entry -> keySet.contains(entry.getKey()))
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        try {
            List<Row> rows = map.entrySet().stream()
                    .map(entry -> Row.from(Cell.from(entry.getKey()), Cell.from(String.valueOf(entry.getValue()))))
                    .toList();

            Table table = Table.builder()
                    .header(Row.from("Key", "Value"))
                    .rows(rows)
                    .widths(Constraint.length(20), Constraint.fill())
                    .columnSpacing(1)
                    .build();

            int lineCount = rows.size() + 3;

            try (InlineDisplay display = InlineDisplay.create(lineCount)) {
                display.render((area, buffer) -> {
                    TableState state = new TableState();
                    table.render(area, buffer, state);
                });
            }
        } catch (Exception e) {
            printErrorMessage("Failed to print object: " + e.getMessage());
        }
    }

    public void printErrorMessage(String message) {
        if (message != null) {
            printStyledMessage(message, ERROR_STYLE);
        }
    }

    public void printWarningMessage(String message) {
        if (message != null) {
            printStyledMessage(message, WARNING_STYLE);
        }
    }

    public void printInfoMessage(String message) {
        if (message != null) {
            printStyledMessage(message, INFO_STYLE);
        }
    }

    public void printSuccessMessage(String message) {
        if (message != null) {
            printStyledMessage(message, SUCCESS_STYLE);
        }
    }

    public void println(String message) {
        terminal.writer().println(message != null ? message : "");
        terminal.flush();
    }

    public void printMarkdown(String markdown) {
        if (markdown == null) {
            throw new IllegalArgumentException("Markdown text cannot be null");
        }

        Text rendered = MarkdownRenderer.render(markdown);
        for (Line line : rendered.lines()) {
            terminal.writer().println(line.rawContent());
        }
        terminal.flush();
    }

    public void pageMarkdown(String markdown) throws IOException, InterruptedException {
        if (markdown == null) {
            throw new IllegalArgumentException("Markdown text cannot be null");
        }

        Text rendered = MarkdownRenderer.render(markdown);
        List<Line> allLines = rendered.lines();

        int terminalHeight = terminal.getHeight();
        if (terminalHeight <= 0) {
            terminalHeight = 24;
        }

        if (allLines.size() <= terminalHeight - 2) {
            for (Line line : allLines) {
                terminal.writer().println(line.rawContent());
            }
            terminal.flush();
            return;
        }

        int viewportHeight = terminalHeight - 1;
        try (InlineDisplay display = InlineDisplay.create(viewportHeight)) {
            int offset = 0;
            boolean running = true;

            while (running) {
                final int currentOffset = offset;
                display.render((area, buffer) -> {
                    for (int i = 0; i < area.height() && (currentOffset + i) < allLines.size(); i++) {
                        Line line = allLines.get(currentOffset + i);
                        buffer.setLine(area.x(), area.y() + i, line);
                    }
                });

                int ch = terminal.reader().read(100);
                if (ch == -1 || ch == -2) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ie) {
                        running = false;
                    }
                    continue;
                }

                switch (ch) {
                    case 'q', 'Q' -> running = false;
                    case 'j', 14 -> offset = Math.min(offset + 1, Math.max(0, allLines.size() - viewportHeight));
                    case 'k', 16 -> offset = Math.max(offset - 1, 0);
                    case ' ', 'f' ->
                        offset = Math.min(offset + viewportHeight, Math.max(0, allLines.size() - viewportHeight));
                    case 'b' -> offset = Math.max(offset - viewportHeight, 0);
                    case 'g' -> offset = 0;
                    case 'G' -> offset = Math.max(0, allLines.size() - viewportHeight);
                    default -> {}
                }
            }
        }
    }

    private void printStyledMessage(String message, Style style) {
        if (plainMode) {
            terminal.writer().println(message);
        } else {
            try (InlineDisplay display = InlineDisplay.create(1)) {
                display.println(Text.from(Span.styled(message, style)));
            } catch (IOException e) {
                terminal.writer().println(message);
            }
        }
        terminal.flush();
    }

    private Map<String, Object> convertToMap(Object obj) {
        if (obj == null) {
            return Map.of();
        }

        try {
            return objectMapper.convertValue(obj, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert object to map: " + e.getMessage(), e);
        }
    }

    private static <T> T validateNotNull(T object, String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
        return object;
    }
}
