package com.advanceai.calculator.engine

/**
 * Public entry point of the math engine: parse + evaluate + format, with every
 * failure turned into a friendly message instead of an exception.
 */
object CalculatorEngine {

    fun evaluate(input: String, ctx: MathContext): EngineResult {
        if (input.isBlank()) return EngineResult.Err("Nothing to calculate")
        return try {
            val value = valueOf(input, ctx)
            val text = Formatter.value(
                value,
                if (ctx.polarMode) ctx.angleMode else null,
                ctx.display
            )
            EngineResult.Ok(value, text)
        } catch (e: MathError) {
            EngineResult.Err(e.message ?: "Math error", e.position)
        } catch (e: StackOverflowError) {
            EngineResult.Err("Expression is too deeply nested")
        } catch (e: Exception) {
            EngineResult.Err(e.message ?: "Could not calculate this expression")
        }
    }

    /** Evaluates and returns the raw value, letting [MathError] escape. */
    fun valueOf(input: String, ctx: MathContext): Value {
        val expr = parse(input)
        return Evaluator.eval(expr, ctx)
    }

    fun parse(input: String): Expr = Parser(Lexer.tokenize(input)).parse()

    /** Evaluates with a variable temporarily bound (used by calculus and plotting). */
    fun valueWith(input: String, parsed: Expr?, variable: String, value: Double, ctx: MathContext): Double {
        val expr = parsed ?: parse(input)
        val previous = ctx.valueOf(variable)
        ctx.setVariable(variable, value)
        try {
            val result = Evaluator.eval(expr, ctx)
            return Evaluator.realOrNull(result, "Expression")
        } finally {
            if (previous == null) ctx.variables.remove(variable.lowercase())
            else ctx.setValue(variable, previous)
        }
    }
}
