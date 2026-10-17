package com.advanceai.calculator.engine

/**
 * Recursive-descent parser.
 *
 * Grammar (highest to lowest precedence):
 *
 *   expr      := term (('+' | '-') term)*
 *   term      := unary (('*' | '/' | 'mod' | implicit) unary)*
 *   unary     := ('+' | '-') unary | power
 *   power     := postfix ('^' unary)?            // right associative
 *   postfix   := primary ('!' | '%' | '!!')*
 *   primary   := number | constant | variable
 *              | ident '(' args ')'
 *              | '(' expr ')'
 *              | '[' matrixLiteral ']'
 *
 * Implicit multiplication is supported: `2π`, `3(4+5)`, `2sin(x)`, `(1+2)(3+4)`.
 */
class Parser(private val tokens: List<Token>) {

    private var index = 0

    private fun peek(): Token = tokens[index]
    private fun peek(ahead: Int): Token = tokens[minOf(index + ahead, tokens.size - 1)]
    private fun next(): Token = tokens[index++]
    private fun at(type: TokType): Boolean = peek().type == type
    private fun match(type: TokType): Boolean {
        if (at(type)) {
            index++
            return true
        }
        return false
    }

    private fun expect(type: TokType, what: String): Token {
        if (!at(type)) {
            val t = peek()
            val found = if (t.type == TokType.EOF) "end of expression" else "'${t.text}'"
            throw MathError("Expected $what but found $found", t.pos)
        }
        return next()
    }

    fun parse(): Expr {
        val e = parseExpr()
        if (!at(TokType.EOF)) {
            val t = peek()
            throw MathError("Unexpected '${t.text}'", t.pos)
        }
        return e
    }

    /** True when the token can begin a primary expression (used for implicit multiplication). */
    private fun startsPrimary(t: Token): Boolean = when (t.type) {
        TokType.NUMBER, TokType.LPAREN, TokType.LBRACKET -> true
        TokType.IDENT -> true
        else -> false
    }

    private fun parseExpr(): Expr {
        var left = parseTerm()
        while (true) {
            left = when {
                match(TokType.PLUS) -> BinaryExpr("+", left, parseTerm())
                match(TokType.MINUS) -> BinaryExpr("-", left, parseTerm())
                else -> return left
            }
        }
    }

    private fun parseTerm(): Expr {
        var left = parseUnary()
        while (true) {
            left = when {
                match(TokType.STAR) -> BinaryExpr("*", left, parseUnary())
                match(TokType.SLASH) -> BinaryExpr("/", left, parseUnary())
                at(TokType.IDENT) && peek().text.equals("mod", ignoreCase = true) -> {
                    next()
                    BinaryExpr("%", left, parseUnary())
                }
                at(TokType.PERCENT) && isBinaryModulo(1) -> {
                    next()
                    BinaryExpr("%", left, parseUnary())
                }
                startsPrimary(peek()) -> BinaryExpr("*", left, parseUnary())
                else -> return left
            }
        }
    }

    /**
     * `%` is a postfix percent sign when what follows can end an expression
     * (operator, closing bracket, comma or end of input) and a binary modulo
     * operator otherwise. `50%2` is modulo, `50%+2` is 0.5 + 2.
     */
    private fun isBinaryModulo(ahead: Int): Boolean = when (peek(ahead).type) {
        TokType.NUMBER, TokType.IDENT, TokType.LPAREN, TokType.LBRACKET -> true
        else -> false
    }

    private fun parseUnary(): Expr {
        return when {
            match(TokType.MINUS) -> UnaryExpr(TokType.MINUS, parseUnary())
            match(TokType.PLUS) -> UnaryExpr(TokType.PLUS, parseUnary())
            else -> parsePower()
        }
    }

    private fun parsePower(): Expr {
        val base = parsePostfix()
        return if (match(TokType.CARET)) {
            BinaryExpr("^", base, parseUnary())
        } else {
            base
        }
    }

    private fun parsePostfix(): Expr {
        var expr = parsePrimary()
        while (true) {
            expr = when {
                at(TokType.BANG) -> {
                    val t = next()
                    if (at(TokType.BANG)) {
                        next() // tolerate `n!!` like `n!`
                    }
                    FactorialExpr(expr, t.pos)
                }
                at(TokType.PERCENT) && !isBinaryModulo(1) -> {
                    next()
                    PercentExpr(expr)
                }
                else -> return expr
            }
        }
    }

    private fun parsePrimary(): Expr {
        val t = peek()
        return when (t.type) {
            TokType.NUMBER -> {
                next()
                NumLit(t.text.toDoubleOrNull() ?: throw MathError("Invalid number '${t.text}'", t.pos))
            }

            TokType.LPAREN -> {
                next()
                val inner = parseExpr()
                expect(TokType.RPAREN, "')'")
                inner
            }

            TokType.LBRACKET -> parseMatrixLiteral()

            TokType.IDENT -> parseIdent()

            else -> {
                val found = if (t.type == TokType.EOF) "end of expression" else "'${t.text}'"
                throw MathError("Unexpected $found", t.pos)
            }
        }
    }

    private fun parseIdent(): Expr {
        val t = next()
        val name = t.text
        if (at(TokType.LPAREN)) {
            next()
            val args = ArrayList<Expr>()
            if (!at(TokType.RPAREN)) {
                do {
                    args.add(parseExpr())
                } while (match(TokType.COMMA))
            }
            expect(TokType.RPAREN, "')'")
            return CallExpr(name.lowercase(), args, t.pos)
        }
        return VarRef(name, t.pos)
    }

    /** `[1, 2; 3, 4]` builds a matrix, `[1, 2, 3]` builds a row vector. */
    private fun parseMatrixLiteral(): Expr {
        val start = expect(TokType.LBRACKET, "'['").pos
        val rows = ArrayList<List<Expr>>()
        if (at(TokType.RBRACKET)) {
            throw MathError("Empty matrix", start)
        }
        while (true) {
            val row = ArrayList<Expr>()
            while (true) {
                row.add(parseExpr())
                if (!match(TokType.COMMA)) break
            }
            rows.add(row)
            if (!match(TokType.SEMI)) break
        }
        expect(TokType.RBRACKET, "']'")
        return MatrixLitExpr(rows, start)
    }
}
