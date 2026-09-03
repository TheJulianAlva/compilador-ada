package com.compiladorada.ide;

import com.compiladorada.lexico.PalabrasReservadas;
import org.fife.ui.rsyntaxtextarea.AbstractTokenMaker;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMap;
import org.fife.ui.rsyntaxtextarea.TokenTypes;

import javax.swing.text.Segment;

/**
 * {@link AbstractTokenMaker} para el subconjunto Ada del proyecto: colorea
 * palabras reservadas (vía {@link PalabrasReservadas#TODAS}), comentarios de
 * línea {@code --}, cadenas, literales de carácter y números.
 */
public class AdaTokenMaker extends AbstractTokenMaker {

    @Override
    public TokenMap getWordsToHighlight() {
        TokenMap tm = new TokenMap(true); // ignora mayúsculas: Ada es case-insensitive
        for (String kw : PalabrasReservadas.TODAS) {
            tm.put(kw, TokenTypes.RESERVED_WORD);
        }
        return tm;
    }

    @Override
    public void addToken(Segment segment, int start, int end, int tokenType, int startOffset) {
        if (tokenType == TokenTypes.IDENTIFIER) {
            int value = wordsToHighlight.get(segment, start, end);
            if (value != -1) {
                tokenType = value;
            }
        }
        super.addToken(segment, start, end, tokenType, startOffset);
    }

    @Override
    public Token getTokenList(Segment text, int startTokenType, int startOffset) {
        resetTokenList();

        char[] array = text.array;
        int offset = text.offset;
        int end = offset + text.count;
        int newStartOffset = startOffset - offset;
        int currentTokenStart = offset;
        int currentTokenType = startTokenType;

        for (int i = offset; i < end; i++) {
            char c = array[i];
            switch (currentTokenType) {
                case TokenTypes.NULL:
                    currentTokenStart = i;
                    if (c == '-' && i + 1 < end && array[i + 1] == '-') {
                        currentTokenType = TokenTypes.COMMENT_EOL;
                    } else if (c == '"') {
                        currentTokenType = TokenTypes.LITERAL_STRING_DOUBLE_QUOTE;
                    } else if (c == '\'' && i + 2 < end && array[i + 2] == '\'') {
                        currentTokenType = TokenTypes.LITERAL_CHAR;
                    } else if (Character.isWhitespace(c)) {
                        currentTokenType = TokenTypes.WHITESPACE;
                    } else if (Character.isDigit(c)) {
                        currentTokenType = TokenTypes.LITERAL_NUMBER_DECIMAL_INT;
                    } else if (Character.isLetter(c)) {
                        currentTokenType = TokenTypes.IDENTIFIER;
                    } else {
                        addToken(text, i, i, TokenTypes.IDENTIFIER, newStartOffset + i);
                        currentTokenType = TokenTypes.NULL;
                    }
                    break;

                case TokenTypes.COMMENT_EOL:
                    // hasta fin de línea: se consume todo
                    break;

                case TokenTypes.LITERAL_CHAR:
                    if (c == '\'') {
                        addToken(text, currentTokenStart, i,
                                TokenTypes.LITERAL_CHAR, newStartOffset + currentTokenStart);
                        currentTokenType = TokenTypes.NULL;
                    }
                    break;

                case TokenTypes.LITERAL_STRING_DOUBLE_QUOTE:
                    if (c == '"') {
                        addToken(text, currentTokenStart, i,
                                TokenTypes.LITERAL_STRING_DOUBLE_QUOTE, newStartOffset + currentTokenStart);
                        currentTokenType = TokenTypes.NULL;
                    }
                    break;

                case TokenTypes.WHITESPACE:
                    if (!Character.isWhitespace(c)) {
                        addToken(text, currentTokenStart, i - 1,
                                TokenTypes.WHITESPACE, newStartOffset + currentTokenStart);
                        currentTokenType = TokenTypes.NULL;
                        i--; // reprocesa c
                    }
                    break;

                case TokenTypes.LITERAL_NUMBER_DECIMAL_INT:
                    if (!(Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '#')) {
                        addToken(text, currentTokenStart, i - 1,
                                TokenTypes.LITERAL_NUMBER_DECIMAL_INT, newStartOffset + currentTokenStart);
                        currentTokenType = TokenTypes.NULL;
                        i--;
                    }
                    break;

                case TokenTypes.IDENTIFIER:
                    if (!(Character.isLetterOrDigit(c) || c == '_')) {
                        addToken(text, currentTokenStart, i - 1,
                                TokenTypes.IDENTIFIER, newStartOffset + currentTokenStart);
                        currentTokenType = TokenTypes.NULL;
                        i--;
                    }
                    break;
            }
        }

        switch (currentTokenType) {
            case TokenTypes.NULL:
                addNullToken();
                break;
            case TokenTypes.COMMENT_EOL:
                addToken(text, currentTokenStart, end - 1, TokenTypes.COMMENT_EOL,
                        newStartOffset + currentTokenStart);
                addNullToken();
                break;
            default:
                addToken(text, currentTokenStart, end - 1, currentTokenType,
                        newStartOffset + currentTokenStart);
                addNullToken();
                break;
        }

        return firstToken;
    }
}
