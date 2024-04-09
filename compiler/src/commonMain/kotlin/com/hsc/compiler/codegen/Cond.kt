package com.hsc.compiler.codegen

import com.hsc.compiler.ir.action.Action
import com.hsc.compiler.ir.action.Comparison
import com.hsc.compiler.ir.action.Condition
import com.hsc.compiler.ir.ast.BinOpKind
import com.hsc.compiler.ir.ast.Expr
import com.hsc.compiler.ir.ast.ExprKind
import com.hsc.compiler.span.Span

/**
 * Transforms an [ExprKind.If] into an [Action.Conditional].
 */
fun ActionTransformer.transformCond(cond: ExprKind.If): Action {
    val block = transformBlock(cond.block)
    val other = cond.other?.let { transformBlock(it) } ?: emptyList()
    // Represents whether the conditional is an AND conditional or an OR conditional
    val and = when (val kind = cond.expr.kind) {
        is ExprKind.Binary -> {
            when (kind.kind) {
                BinOpKind.And -> true
                BinOpKind.Or -> false
                else -> {
                    // single condition, unwrap and return
                    val single = unwrapCond(cond.expr)
                    return Action.Conditional(listOf(single), false, block, other)
                }
            }
        }
        else -> {
            // This should almost certainly never happen unless in `strict` mode.
            // But on the off chance it does, here's a bug that creates a bug!
            strict(cond.expr.span) {
                unsupported("complex expressions", cond.expr.span)
            }
        }
    }

    val res = mutableListOf<Expr>()
    val dfs = mutableListOf(cond.expr)
    while (dfs.isNotEmpty()) {
        val expr = dfs.removeLast()
        when (val kind = expr.kind) {
            is ExprKind.Binary -> {
                when (kind.kind) {
                    BinOpKind.And, BinOpKind.Or -> {
                        if ((kind.kind == BinOpKind.And && and) || (kind.kind == BinOpKind.Or && !and)) {
                            // Operands match
                            dfs.add(kind.a)
                            dfs.add(kind.b)
                        } else {
                            strict(expr.span) {
                                // This span should be fine
                                val span = Span(kind.a.span.hi + 1, kind.b.span.lo - 1, kind.a.span.fid)
                                val type = if (and) "&&" else "||"
                                val not = if (!and) "&&" else "||"
                                sess.dcx().err("mismatched conditional operand $not, expected $type", span)
                            }
                        }
                    }
                    BinOpKind.Lt, BinOpKind.Le, BinOpKind.Gt, BinOpKind.Ge, BinOpKind.Eq -> {
                        res.add(expr) // This is a good expression, probably
                    }
                    BinOpKind.Ne -> {
                        strict(expr.span) {
                            sess.dcx().err("cannot use != in `strict` mode", expr.span)
                        }
                    }
                    else -> {
                        strict(expr.span) {
                            sess.dcx().err("cannot use complex expressions in `strict` mode", expr.span)
                        }
                    }
                }
            }
            is ExprKind.Action -> {
                if (!kind.boolean) {
                    sess.dcx().err("expected boolean", expr.span)
                } else {
                    res.add(expr)
                }
            }
            else -> {
                throw sess.dcx().err("expected binary expression, found ${kind.str()}", expr.span)
            }
        }
    }

    return Action.Conditional(res.map(this::unwrapCond), !and, block, other)
}

private fun ActionTransformer.unwrapCond(cond: Expr): Condition {
    return when (val kind = cond.kind) {
        is ExprKind.Action -> {
            when (kind.name) {
                "is_sneaking" -> {
                    expectArgs(cond.span, kind.exprs, 0)
                    Condition.PlayerSneaking
                }
                else -> error("unknown built-in action")
            }
        }
        is ExprKind.Binary -> {
            val comparison = when (kind.kind) {
                BinOpKind.Lt -> Comparison.Lt
                BinOpKind.Le -> Comparison.Le
                BinOpKind.Gt -> Comparison.Gt
                BinOpKind.Ge -> Comparison.Ge
                BinOpKind.Eq -> Comparison.Eq
                else -> {
                    throw sess.dcx().bug("unexpected bin op", cond.span)
                }
            }
            val ident = when (val a = kind.a.kind) {
                is ExprKind.Var -> a.ident
                else -> {
                    throw sess.dcx().err("left side of a comparison must be a variable", cond.span)
                }
            }
            val other = unwrapStatValue(kind.b)
            if (ident.global) {
                Condition.GlobalStatRequirement(ident.name, comparison, other)
            } else {
                Condition.PlayerStatRequirement(ident.name, comparison, other)
            }
        }
        is ExprKind.Lit -> {
            // This is dumb! 0L / 1L
            TODO("handle this bullshit, or don't, I don't care")
        }
        else -> {
            throw sess.dcx().bug("unexpected conditional", cond.span)
        }
    }
}

// Note: && holds higher precedence than || in parser
// This should make the algorithm simpler.

// BEWARE! PROCEED INTO RAMBLINGS WITH CAUTION!

// x || y && z || w

// if (x || y) -> temp = 1
// if (z || w) -> temp++
// if (temp == 2) -> hsl:if
// else -> hsl:else

// x && y || z || w

// if (y || z || w) -> temp = 1
// if (x && temp == 1) hsl:if
// else -> hsl:else

// x && y || z && w || a || b || c

// if (y || z) -> temp = 1
// if (w || a || b || c) -> temp++
// if (x && temp == 2) hsl:if
// else -> hsl:else

// (x && y) || (z && w)

// if (x && y) -> temp = 1
// if (z && w) -> temp = 1
// if (temp == 1) -> hsl:if
// else -> hsl:else

// (x && y) || z <-- single value end case

// if (x && y) -> temp = 1
// if (z || temp == 1) -> hsl:if
// else -> hsl:else


// if ((x && y) || z) && w || a

// if (x && y) -> temp = 1
// if (z || temp == 1) -> temp = 2
// if (w || a) -> temp2 = 1
// if (temp == 2 && temp2 == 1) -> hsl:if
// else -> hsl:else

// if ((x && y) || z) && w && a

// if (x && y) -> temp = 1
// if (z || temp == 1) -> temp = 2
// if (w && a && temp == 2) -> hsl:if
// else -> hsl:else