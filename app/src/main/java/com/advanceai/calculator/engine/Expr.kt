package com.advanceai.calculator.engine

/** Expression tree produced by [Parser]. */
sealed interface Expr

data class NumLit(val value: Double) : Expr
data class VarRef(val name: String, val pos: Int) : Expr
data class UnaryExpr(val op: TokType, val operand: Expr) : Expr
data class BinaryExpr(val op: String, val left: Expr, val right: Expr, val pos: Int = -1) : Expr
data class FactorialExpr(val operand: Expr, val pos: Int) : Expr
data class PercentExpr(val operand: Expr) : Expr
data class CallExpr(val name: String, val args: List<Expr>, val pos: Int) : Expr
data class MatrixLitExpr(val rows: List<List<Expr>>, val pos: Int) : Expr
