package ai.wanaku.core.persistence.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import ai.wanaku.capabilities.sdk.api.types.LabelsAwareEntity;
import ai.wanaku.core.util.StringHelper;

public class LabelExpressionParser {

    private List<Token> tokens;
    private int position;

    public LabelExpressionParser() {}

    public static Predicate<LabelsAwareEntity<?>> parse(String expression) throws LabelExpressionParseException {
        LabelExpressionParser parser = new LabelExpressionParser();
        return parser.parseInternal(expression);
    }

    private Predicate<LabelsAwareEntity<?>> parseInternal(String expression) throws LabelExpressionParseException {
        if (StringHelper.isBlank(expression)) {
            throw new LabelExpressionParseException("Expression string cannot be null or empty");
        }

        String sanitized = sanitize(expression);
        this.tokens = tokenize(sanitized);
        this.position = 0;

        if (tokens.isEmpty()) {
            throw new LabelExpressionParseException("No valid tokens found in expression");
        }

        Predicate<LabelsAwareEntity<?>> result = parseExpression();

        if (position < tokens.size()) {
            throw new LabelExpressionParseException(
                    "Unexpected token after expression: %s".formatted(tokens.get(position).value));
        }

        return result;
    }

    private String sanitize(String expression) throws LabelExpressionParseException {
        if (expression.length() > 1000) {
            throw new LabelExpressionParseException("Expression string too long (max 1000 characters)");
        }

        Pattern validPattern = Pattern.compile("^[a-zA-Z0-9\\s&|!()=_\\-./]+$");
        if (!validPattern.matcher(expression).matches()) {
            throw new LabelExpressionParseException(
                    "Expression contains invalid characters. Only alphanumeric, operators (&|!=), parentheses, "
                            + "hyphen, underscore, and dot are allowed");
        }

        return expression.trim();
    }

    private List<Token> tokenize(String expression) throws LabelExpressionParseException {
        List<Token> result = new ArrayList<>();
        int i = 0;

        while (i < expression.length()) {
            char c = expression.charAt(i);

            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

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

            throw new LabelExpressionParseException("Unexpected character: %s".formatted(c));
        }

        return result;
    }

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
        if (match(TokenType.LPAREN)) {
            Predicate<LabelsAwareEntity<?>> expr = parseExpression();
            if (!match(TokenType.RPAREN)) {
                throw new LabelExpressionParseException("Expected ')' after expression");
            }
            return expr;
        }

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
            throw new LabelExpressionParseException("Expected '=' or '!=' after label key '%s'".formatted(key.value));
        }
    }

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
            throw new LabelExpressionParseException("%s but got '%s'".formatted(errorMessage, token.value));
        }
        position++;
        return token;
    }

    private enum TokenType {
        AND,
        OR,
        NOT,
        EQUALS,
        NOT_EQUALS,
        LPAREN,
        RPAREN,
        IDENTIFIER
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

    public static class LabelExpressionParseException extends Exception {
        public LabelExpressionParseException(String message) {
            super(message);
        }
    }
}
