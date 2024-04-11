package com.hsc.compiler.lowering.passes

import com.hsc.compiler.ir.ast.*
import com.hsc.compiler.lowering.LoweringCtx

/**
 * A pass that will flatten temp vars that are immediately reassigned.
 *
 * Should *always* run after `FlattenComplexExpressionsPass`!
 *
 * eg:
 * ```
 * _temp = 5
 * x = _temp
 * ```
 * becomes:
 * ```
 * x = 5
 * ```
 */
object FlattenTempReassignPass : AstPass {

    override fun run(ctx: LoweringCtx) {
        val functions = ctx.query<Item>().filter { it.kind is ItemKind.Fn }
        functions.forEach {
            FlattenTempReassignVisitor.visitItem(it)
        }
    }

}

private object FlattenTempReassignVisitor : BlockAwareVisitor() {

    var prevTempAssign: Expr? = null
    var prevIdent: Ident? = null

    override fun visitBlock(block: Block) {
        prevTempAssign = null
        prevIdent = null
        super.visitBlock(block)
    }

    override fun visitStmt(stmt: Stmt) {
        when (val kind = stmt.kind) {
            is StmtKind.Assign -> {
                when (val exprKind = kind.expr.kind) {
                    is ExprKind.Var -> {
                        if (exprKind.ident == prevIdent) {
                            kind.expr = prevTempAssign!!
                            currentBlock.stmts.removeAt(currentPosition - 1)
                            added(-1)
                        } else if (exprKind.ident == kind.ident) {
                            currentBlock.stmts.removeAt(currentPosition)
                            added(-1)
                            super.visitStmt(stmt)
                            return
                        }
                    }

                    else -> {}
                }

                if (kind.ident.name.startsWith("_")) {
                    prevTempAssign = kind.expr
                    prevIdent = kind.ident
                }
                else {
                    prevTempAssign = null
                    prevIdent = null
                }
            }
            else -> {
                if (kind is StmtKind.Expr && kind.expr.kind is ExprKind.Var) {
                    // Remove useless vars
                    currentBlock.stmts.removeAt(currentPosition)
                    added(-1)
                }
                prevTempAssign = null
                prevIdent = null
            }
        }
        super.visitStmt(stmt)
    }

}