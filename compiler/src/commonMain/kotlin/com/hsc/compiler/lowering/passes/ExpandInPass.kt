package com.hsc.compiler.lowering.passes

import com.hsc.compiler.ir.ast.BinOpKind
import com.hsc.compiler.ir.ast.Expr
import com.hsc.compiler.ir.ast.ExprKind
import com.hsc.compiler.lowering.LoweringCtx

object ExpandInPass : AstPass {

    override fun run(ctx: LoweringCtx) {
        ctx.query<Expr>().forEach { expr ->
            expandIn(ctx, expr)
        }
    }

}

private fun expandIn(ctx: LoweringCtx, expr: Expr) {
    when (val kind = expr.kind) {
        is ExprKind.Binary -> {
            if (kind.kind == BinOpKind.In) {
                val range = when (kind.b.kind) {
                    is ExprKind.Range -> (kind.b.kind as ExprKind.Range).range
                    else -> {
                        throw ctx.dcx().err("expected range, found ${kind.b.kind.str()}", kind.b.span)
                    }
                }

                val lower = Expr(expr.span, ExprKind.Binary(BinOpKind.Ge, kind.a.deepCopy(), range.lo))
                val higher = Expr(expr.span, ExprKind.Binary(BinOpKind.Le, kind.a.deepCopy(), range.hi))
                expr.kind = ExprKind.Binary(BinOpKind.And, lower, higher)
            }
        }
        else -> {}
    }
}