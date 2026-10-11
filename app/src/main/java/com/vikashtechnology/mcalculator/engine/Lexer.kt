package com.vikashtechnology.mcalculator.engine

/** Token kinds produced by the lexer. */
enum class TokType {
    NUMBER, IDENT, PLUS, MINUS, STAR, SLASH, CARET, PERCENT, BANG,
    LPAREN, RPAREN, COMMA, SEMI, LBRACKET, RBRACKET, EOF
}

data class Token(val type: TokType, val text: String, val pos: Int)

/**
 * Converts the raw input string into tokens.
 *
 * The input is normalised first so that typographic characters coming from the
 * keypad or a paste (×, ÷, −, π, √, superscripts, …) behave like ASCII ones.
 */
object Lexer {

    fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c == '×' || c == '·' || c == '⋅' -> sb.append('*')
                c == '÷' -> sb.append('/')
                c == '−' || c == '–' || c == '—' -> sb.append('-')
                c == 'π' -> sb.append("pi")
                c == 'τ' -> sb.append("tau")
                c == 'φ' -> sb.append("phi")
                c == '√' -> sb.append("sqrt")
                c == '≤' -> sb.append("<=")
                c == '≥' -> sb.append(">=")
                c == '≠' -> sb.append("!=")
                c == '∞' -> sb.append("Infinity")
                c == '⁄' -> sb.append('/')
                c == '⁰' -> sb.append("^0")
                c == '¹' -> sb.append("^1")
                c == '²' -> sb.append("^2")
                c == '³' -> sb.append("^3")
                c == '⁴' -> sb.append("^4")
                c == 'ᵗ' -> sb.append("^")
                c == '\u00A0' -> sb.append(' ')
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    fun tokenize(raw: String): List<Token> {
        val input = normalize(raw)
        val tokens = ArrayList<Token>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c.isWhitespace() -> i++

                c.isDigit() || (c == '.' && i + 1 < input.length && input[i + 1].isDigit()) -> {
                    val start = i
                    while (i < input.length && input[i].isDigit()) i++
                    if (i < input.length && input[i] == '.') {
                        i++
                        while (i < input.length && input[i].isDigit()) i++
                    }
                    // Scientific notation: 1.5e-3 (only when a digit follows the sign)
                    if (i < input.length && (input[i] == 'e' || input[i] == 'E')) {
                        var j = i + 1
                        if (j < input.length && (input[j] == '+' || input[j] == '-')) j++
                        if (j < input.length && input[j].isDigit()) {
                            i = j
                            while (i < input.length && input[i].isDigit()) i++
                        }
                    }
                    tokens.add(Token(TokType.NUMBER, input.substring(start, i), start))
                }

                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < input.length && (input[i].isLetterOrDigit() || input[i] == '_')) i++
                    tokens.add(Token(TokType.IDENT, input.substring(start, i), start))
                }

                else -> {
                    val type = when (c) {
                        '+' -> TokType.PLUS
                        '-' -> TokType.MINUS
                        '*' -> TokType.STAR
                        '/' -> TokType.SLASH
                        '^' -> TokType.CARET
                        '%' -> TokType.PERCENT
                        '!' -> TokType.BANG
                        '(' -> TokType.LPAREN
                        ')' -> TokType.RPAREN
                        ',' -> TokType.COMMA
                        ';' -> TokType.SEMI
                        '[' -> TokType.LBRACKET
                        ']' -> TokType.RBRACKET
                        else -> throw MathError("Unexpected character '$c'", i)
                    }
                    tokens.add(Token(type, c.toString(), i))
                    i++
                }
            }
        }
        tokens.add(Token(TokType.EOF, "", input.length))
        return tokens
    }
}
