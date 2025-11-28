package ai.wanaku.core.mcp.util;

import ai.wanaku.capabilities.sdk.api.types.LabelsAwareEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Recursive descent parser for label expression filters.
 * <p>
 * Parses expressions like:
 * <ul>
 *   <li>"category=weather"</li>
 *   <li>"category=weather &amp; action=forecast"</li>
 *   <li>"category=weather &amp; !action=forecast"</li>
 *   <li>"(category=weather | category=news) &amp; !action=forecast"</li>
 * </ul>
 * <p>
 * Grammar:
 * <pre>
 * expression  ::= orExpr
 * orExpr      ::= andExpr ('|' andExpr)*
 * andExpr     ::= notExpr ('&amp;' notExpr)*
 * notExpr     ::= '!' notExpr | primary
 * primary     ::= '(' expression ')' | comparison
 * comparison  ::= IDENTIFIER '=' STRING | IDENTIFIER '!=' STRING
 * </pre>
 */
public class LabelExpressionParser {

    private List<Token> tokens;
    private int position;

    /**
     * Constructs a new LabelExpressionParser instance.
     * Use the static {@link #parse(String)} method for parsing label expressions.
     */
    public LabelExpressionParser() {
        // Default constructor
    }

    /**
     * Parses a label expression string into a predicate that can be used to filter label-aware entities.
     *
     * @param expression the expression string to parse
     * @return a predicate that tests if an entity's labels match the expression
     * @throws LabelExpressionParseException if the expression is invalid
     */
    public static Predicate<LabelsAwareEntity<?>> parse(String expression) throws LabelExpressionParseException {
        LabelExpressionParser parser = new LabelExpressionParser();
        return parser.parseInternal(expression);
    }

    /**
     * Internal parsing method that uses instance state.
     *
     * @param expression the expression string to parse
     * @return a predicate that tests if an entity's labels match the expression
     * @throws LabelExpressionParseException if the expression is invalid
     */
    private Predicate<LabelsAwareEntity<?>> parseInternal(String expression) throws LabelExpressionParseException {
        if (expression == null || expression.trim().isEmpty()) {
            throw new LabelExpressionParseException("Expression string cannot be null or empty");
        }

        // Sanitize and tokenize
        String sanitized = sanitize(expression);
        this.tokens = tokenize(sanitized);
        this.position = 0;

        if (tokens.isEmpty()) {
            throw new LabelExpressionParseException("No valid tokens found in expression");
        }

        Predicate<LabelsAwareEntity<?>> result = parseExpression();

        // Ensure all tokens were consumed
        if (position < tokens.size()) {
            throw new LabelExpressionParseException("Unexpected token after expression: " + tokens.get(position).value);
        }

        return result;
    }

    /**
     * Sanitizes the input expression string to ensure parser safety.
     * <ul>
     *   <li>Validates character whitelist (prevents parser errors from unexpected characters)</li>
     *   <li>Limits length to 1000 characters (prevents DoS attacks)</li>
     *   <li>Ensures only valid label expression syntax characters are present</li>
     * </ul>
     *
     * @param expression the raw expression string
     * @return sanitized expression string
     * @throws LabelExpressionParseException if the expression contains invalid characters or is too long
     */
    private String sanitize(String expression) throws LabelExpressionParseException {
        if (expression.length() > 1000) {
            throw new LabelExpressionParseException("Expression string too long (max 1000 characters)");
        }

        // Allow only safe characters: alphanumeric, operators, parentheses, whitespace, hyphen, underscore, dot
        Pattern validPattern = Pattern.compile("^[a-zA-Z0-9\\s&|!()=_\\-./]+$");
        if (!validPattern.matcher(expression).matches()) {
            throw new LabelExpressionParseException(
                    "Expression contains invalid characters. Only alphanumeric, operators (&|!=), parentheses, "
                            + "hyphen, underscore, and dot are allowed");
        }

        return expression.trim();
    }

    /**
     * Tokenizes the sanitized expression string.
     *
     * @param expression the sanitized expression string
     * @return list of tokens
     * @throws LabelExpressionParseException if tokenization fails
     */
    private List<Token> tokenize(String expression) throws LabelExpressionParseException {
        List<Token> result = new ArrayList<>();
        int i = 0;

        while (i < expression.length()) {
            char c = expression.charAt(i);

            // Skip whitespace
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // Operators and parentheses
            switch (c) {
                case '&':
                    result.add(new Token(TokenType.AND, "&"));
                    i++;
                    continue;
                case '|':
                    result.add(new Token(TokenType.OR, "|"));
                    i++;
                    continue;
                case '!':
                    // Check if it's != or just !
                    if (i + 1 < expression.length() && expression.charAt(i + 1) == '=') {
                        result.add(new Token(TokenType.NOT_EQUALS, "!="));
                        i += 2;
                    } else {
                        result.add(new Token(TokenType.NOT, "!"));
                        i++;
                    }
                    continue;
                case '=':
                    result.add(new Token(TokenType.EQUALS, "="));
                    i++;
                    continue;
                case '(':
                    result.add(new Token(TokenType.LPAREN, "("));
                    i++;
                    continue;
                case ')':
                    result.add(new Token(TokenType.RPAREN, ")"));
                    i++;
                    continue;
            }

            // Identifiers (keys and values)
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.') {
                StringBuilder sb = new StringBuilder();
                while (i < expression.length()) {
                    c = expression.charAt(i);
                    if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == '/') {
                        sb.append(c);
                        i++;
                    } else {
                        break;
                    }
                }
                result.add(new Token(TokenType.IDENTIFIER, sb.toString()));
                continue;
            }

            throw new LabelExpressionParseException("Unexpected character: " + c);
        }

        return result;
    }

    // Recursive descent parsing methods

    private Predicate<LabelsAwareEntity<?>> parseExpression() throws LabelExpressionParseException {
        return parseOrExpression();
    }

    private Predicate<LabelsAwareEntity<?>> parseOrExpression() throws LabelExpressionParseException {
        Predicate<LabelsAwareEntity<?>> left = parseAndExpression();

        while (match(TokenType.OR)) {
            Predicate<LabelsAwareEntity<?>> right = parseAndExpression();
            left = left.or(right);
        }

        return left;
    }

    private Predicate<LabelsAwareEntity<?>> parseAndExpression() throws LabelExpressionParseException {
        Predicate<LabelsAwareEntity<?>> left = parseNotExpression();

        while (match(TokenType.AND)) {
            Predicate<LabelsAwareEntity<?>> right = parseNotExpression();
            left = left.and(right);
        }

        return left;
    }

    private Predicate<LabelsAwareEntity<?>> parseNotExpression() throws LabelExpressionParseException {
        if (match(TokenType.NOT)) {
            Predicate<LabelsAwareEntity<?>> expr = parseNotExpression();
            return expr.negate();
        }

        return parsePrimary();
    }

    private Predicate<LabelsAwareEntity<?>> parsePrimary() throws LabelExpressionParseException {
        // Parenthesized expression
        if (match(TokenType.LPAREN)) {
            Predicate<LabelsAwareEntity<?>> expr = parseExpression();
            if (!match(TokenType.RPAREN)) {
                throw new LabelExpressionParseException("Expected ')' after expression");
            }
            return expr;
        }

        // Comparison
        return parseComparison();
    }

    private Predicate<LabelsAwareEntity<?>> parseComparison() throws LabelExpressionParseException {
        Token key = consume(TokenType.IDENTIFIER, "Expected label key");

        if (match(TokenType.EQUALS)) {
            Token value = consume(TokenType.IDENTIFIER, "Expected label value after '='");
            return entity -> value.value.equals(entity.getLabels().get(key.value));
        } else if (match(TokenType.NOT_EQUALS)) {
            Token value = consume(TokenType.IDENTIFIER, "Expected label value after '!='");
            return entity -> !value.value.equals(entity.getLabels().get(key.value));
        } else {
            throw new LabelExpressionParseException("Expected '=' or '!=' after label key '" + key.value + "'");
        }
    }

    // Helper methods

    private boolean match(TokenType type) {
        if (position >= tokens.size()) {
            return false;
        }
        if (tokens.get(position).type == type) {
            position++;
            return true;
        }
        return false;
    }

    private Token consume(TokenType type, String errorMessage) throws LabelExpressionParseException {
        if (position >= tokens.size()) {
            throw new LabelExpressionParseException(errorMessage + " (end of input)");
        }
        Token token = tokens.get(position);
        if (token.type != type) {
            throw new LabelExpressionParseException(errorMessage + " but got '" + token.value + "'");
        }
        position++;
        return token;
    }

    // Token types and classes

    private enum TokenType {
        AND, // &
        OR, // |
        NOT, // !
        EQUALS, // =
        NOT_EQUALS, // !=
        LPAREN, // (
        RPAREN, // )
        IDENTIFIER // key or value
    }

    private static class Token {
        final TokenType type;
        final String value;

        Token(TokenType type, String value) {
            this.type = type;
            this.value = value;
        }

        @Override
        public String toString() {
            return type + "(" + value + ")";
        }
    }

    /**
     * Exception thrown when parsing fails.
     */
    public static class LabelExpressionParseException extends Exception {
        /**
         * Constructs a new LabelExpressionParseException with the specified detail message.
         *
         * @param message the detail message explaining the parse error
         */
        public LabelExpressionParseException(String message) {
            super(message);
        }
    }
}
