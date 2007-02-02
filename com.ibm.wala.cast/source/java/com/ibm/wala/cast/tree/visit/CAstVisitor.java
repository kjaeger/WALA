/******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *****************************************************************************/
package com.ibm.wala.cast.tree.visit;

import com.ibm.wala.cast.tree.*;
import com.ibm.wala.cast.util.CAstPrinter;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.debug.Trace;

import java.util.*;

/**
 * @author Igor Peshansky
 * Ripped out of Julian's AstTranslator
 * TODO: document me.
 */
public abstract class CAstVisitor {

  /**
   * This interface represents a visitor-specific context.  All
   * it knows is how to get its top-level entity.  It is expected
   * that visitors will have classes implementing this interface
   * to collect visitor-specific information.
   *
   * @author Igor Peshansky
   */
  public interface Context {
    CAstEntity top();
  }

  /**
   * Construct a context for a File entity.
   * @param context a visitor-specific context in which this file was visited
   * @param n the file entity
   */
  protected Context makeFileContext(Context context, CAstEntity n) { return context; }
  /**
   * Construct a context for a Type entity.
   * @param context a visitor-specific context in which this type was visited
   * @param n the type entity
   */
  protected Context makeTypeContext(Context context, CAstEntity n) { return context; }
  /**
   * Construct a context for a Code entity.
   * @param context a visitor-specific context in which the code was visited
   * @param n the code entity
   */
  protected Context makeCodeContext(Context context, CAstEntity n) { return context; }

  /**
   * Construct a context for a LocalScope node.
   * @param context a visitor-specific context in which the local scope was visited
   * @param n the local scope node
   */
  protected Context makeLocalContext(Context context, CAstNode n) { return context; }
  /**
   * Construct a context for an Unwind node.
   * @param context a visitor-specific context in which the unwind was visited
   * @param n the unwind node
   */
  protected Context makeUnwindContext(Context context, CAstNode n, CAstVisitor visitor) { return context; }

  private final Map entityParents = new HashMap();

  /**
   * Get the parent entity for a given entity.
   * @param entity the child entity
   * @return the parent entity for the given entity
   */
  protected CAstEntity getParent(CAstEntity entity) {
    return (CAstEntity) entityParents.get(entity);
  }

  /**
   * Set the parent entity for a given entity.
   * @param entity the child entity
   * @param parent the parent entity
   */
  protected void setParent(CAstEntity entity, CAstEntity parent) {
    entityParents.put(entity, parent);
  }

  /**
   * Entity processing hook; sub-classes are expected to override if they introduce new
   * entity types.
   * Should invoke super.doVisitEntity() for unprocessed entities.
   * @return true if entity was handled
   */
  protected boolean doVisitEntity(CAstEntity n, Context context, CAstVisitor visitor) {
    return false;
  }


  /**
   * Visit scoped entities of an entity using a given iterator.
   * Prerequisite (unchecked): i iterates over entities scoped in n.
   * @param n the parent entity of the entities to process
   * @param i the iterator over some scoped entities of n
   * @param context a visitor-specific context
   */
  public final void visitScopedEntities(CAstEntity n, Map allScopedEntities, Context context, CAstVisitor visitor) {
    for(Iterator i = allScopedEntities.values().iterator(); i.hasNext(); ) {
      visitScopedEntities(n, ((Collection)i.next()).iterator(), context, visitor);
    }
  }

  public final void visitScopedEntities(CAstEntity n, Iterator i, Context context, CAstVisitor visitor) {
    while (i.hasNext()) {
      CAstEntity child = (CAstEntity) i.next();
      setParent(child, n);
      visitor.visitEntities(child, context, visitor);
    }
  }
  /**
   * Recursively visit an entity.
   * @param n the entity to process
   * @param context a visitor-specific context
   */
  public final void visitEntities(final CAstEntity n, Context context, CAstVisitor visitor) {
    if (visitor.enterEntity(n, context, visitor))
      return;
    switch (n.getKind()) {
    case CAstEntity.FILE_ENTITY: {
      Context fileContext = visitor.makeFileContext(context, n);
      if (visitor.visitFileEntity(n, context, fileContext, visitor))
        break;
      visitor.visitScopedEntities(n, n.getAllScopedEntities(), fileContext, visitor);
      visitor.leaveFileEntity(n, context, fileContext, visitor);
      break;
    }
    case CAstEntity.FIELD_ENTITY: {
      if (visitor.visitFieldEntity(n, context, visitor))
        break;
      visitor.leaveFieldEntity(n, context, visitor);
      break;
    }
    case CAstEntity.TYPE_ENTITY: {
      Context typeContext = visitor.makeTypeContext(context, n);
      if (visitor.visitTypeEntity(n, context, typeContext, visitor))
        break;
      visitor.visitScopedEntities(n, n.getAllScopedEntities(), typeContext, visitor);
      visitor.leaveTypeEntity(n, context, typeContext, visitor);
      break;
    }
    case CAstEntity.FUNCTION_ENTITY: {
      Context codeContext = visitor.makeCodeContext(context, n);
      if (visitor.visitFunctionEntity(n, context, codeContext, visitor))
        break;
      // visit the AST if any
      if (n.getAST() != null)
        visitor.visit(n.getAST(), codeContext, visitor);
      // XXX: there may be code that needs to go in here
      // process any remaining scoped children
      visitor.visitScopedEntities(n, n.getScopedEntities(null), codeContext, visitor);
      visitor.leaveFunctionEntity(n, context, codeContext, visitor);
      break;
    }
    case CAstEntity.SCRIPT_ENTITY: {
      Context codeContext = visitor.makeCodeContext(context, n);
      if (visitor.visitScriptEntity(n, context, codeContext, visitor))
        break;
      // visit the AST if any
      if (n.getAST() != null)
        visitor.visit(n.getAST(), codeContext, visitor);
      // XXX: there may be code that needs to go in here
      // process any remaining scoped children
      visitor.visitScopedEntities(n, n.getScopedEntities(null), codeContext, visitor);
      visitor.leaveScriptEntity(n, context, codeContext, visitor);
      break;
    }
    default: {
      if (!visitor.doVisitEntity(n, context, visitor)) {
        Trace.println("No handler for entity " + n.getName());
        Assertions.UNREACHABLE("cannot handle entity of kind" + n.getKind());
      }
    }
    }
    visitor.postProcessEntity(n, context, visitor);
  }

  /**
   * Enter the entity visitor.
   * @param n the entity to process
   * @param context a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean enterEntity(CAstEntity n, Context context, CAstVisitor visitor) { return false; }
  /**
   * Post-process an entity after visiting it.
   * @param n the entity to process
   * @param context a visitor-specific context
   */
  protected void postProcessEntity(CAstEntity n, Context context, CAstVisitor visitor) { return; }

  /**
   * Visit any entity.  Override only this to change behavior for all entities.
   * @param n the entity to process
   * @param context a visitor-specific context
   * @return true if no further processing is needed
   */
  public boolean visitEntity(CAstEntity n, Context context, CAstVisitor visitor) { return false; }
  /**
   * Leave any entity.  Override only this to change behavior for all entities.
   * @param n the entity to process
   * @param context a visitor-specific context
   */
  public void leaveEntity(CAstEntity n, Context context, CAstVisitor visitor) { return; }

  /**
   * Visit a File entity.
   * @param n the entity to process
   * @param context a visitor-specific context
   * @param fileContext a visitor-specific context for this file
   * @return true if no further processing is needed
   */
  protected boolean visitFileEntity(CAstEntity n, Context context, Context fileContext, CAstVisitor visitor) { return visitor.visitEntity(n, context, visitor); }
  /**
   * Leave a File entity.
   * @param n the entity to process
   * @param context a visitor-specific context
   * @param fileContext a visitor-specific context for this file
   */
  protected void leaveFileEntity(CAstEntity n, Context context, Context fileContext, CAstVisitor visitor) { visitor.leaveEntity(n, context, visitor); }
  /**
   * Visit a Field entity.
   * @param n the entity to process
   * @param context a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitFieldEntity(CAstEntity n, Context context, CAstVisitor visitor) { return visitor.visitEntity(n, context, visitor); }
  /**
   * Leave a Field entity.
   * @param n the entity to process
   * @param context a visitor-specific context
   */
  protected void leaveFieldEntity(CAstEntity n, Context context, CAstVisitor visitor) { visitor.leaveEntity(n, context, visitor); }
  /**
   * Visit a Type entity.
   * @param n the entity to process
   * @param context a visitor-specific context
   * @param typeContext a visitor-specific context for this type
   * @return true if no further processing is needed
   */
  protected boolean visitTypeEntity(CAstEntity n, Context context, Context typeContext, CAstVisitor visitor) { return visitor.visitEntity(n, context, visitor); }
  /**
   * Leave a Type entity.
   * @param n the entity to process
   * @param context a visitor-specific context
   * @param typeContext a visitor-specific context for this type
   */
  protected void leaveTypeEntity(CAstEntity n, Context context, Context typeContext, CAstVisitor visitor) { visitor.leaveEntity(n, context, visitor); }
  /**
   * Visit a Function entity.
   * @param n the entity to process
   * @param context a visitor-specific context
   * @param codeContext a visitor-specific context for this function
   * @return true if no further processing is needed
   */
  protected boolean visitFunctionEntity(CAstEntity n, Context context, Context codeContext, CAstVisitor visitor) { return visitor.visitEntity(n, context, visitor); }
  /**
   * Leave a Function entity.
   * @param n the entity to process
   * @param context a visitor-specific context
   * @param codeContext a visitor-specific context for this function
   */
  protected void leaveFunctionEntity(CAstEntity n, Context context, Context codeContext, CAstVisitor visitor) { visitor.leaveEntity(n, context, visitor); }
  /**
   * Visit a Script entity.
   * @param n the entity to process
   * @param context a visitor-specific context
   * @param codeContext a visitor-specific context for this script
   * @return true if no further processing is needed
   */
  protected boolean visitScriptEntity(CAstEntity n, Context context, Context codeContext, CAstVisitor visitor) { return visitor.visitEntity(n, context, visitor); }
  /**
   * Leave a Script entity.
   * @param n the entity to process
   * @param context a visitor-specific context
   * @param codeContext a visitor-specific context for this script
   */
  protected void leaveScriptEntity(CAstEntity n, Context context, Context codeContext, CAstVisitor visitor) { visitor.leaveEntity(n, context, visitor); }

  /**
   *  Node processing hook; sub-classes are expected to override if they 
   * introduce new node types.
   *
   * (Should invoke super.doVisit() for unprocessed nodes.)
   *
   * @return true if node was handled
   */
  protected boolean doVisit(CAstNode n, Context context, CAstVisitor visitor) {
    return false;
  }

  /**
   *  Node processing hook; sub-classes are expected to override if they
   * introduce new node types that appear on the left hand side of assignment
   * operations.
   *
   * (Should invoke super.doVisit() for unprocessed nodes.)
   *
   * @return true if node was handled
   */
  protected boolean doVisitAssignNodes(CAstNode n, Context context, CAstNode v, CAstNode a, CAstVisitor visitor) {
    return false;
  }

  /**
   * Visit children of a node starting at a given index.
   * @param n the parent node of the nodes to process
   * @param start the starting index of the nodes to process
   * @param context a visitor-specific context
   */
  public final void visitChildren(CAstNode n, int start, Context context, CAstVisitor visitor) {
    int end = n.getChildCount();
    for (int i = start; i < end; i++)
      visitor.visit(n.getChild(i), context, visitor);
  }
  /**
   * Visit all children of a node.
   * @param n the parent node of the nodes to process
   * @param context a visitor-specific context
   */
  public final void visitAllChildren(CAstNode n, Context context, CAstVisitor visitor) {
    visitor.visitChildren(n, 0, context, visitor);
  }
  /**
   * Recursively visit a given node.
   * TODO: do assertions about structure belong here?
   * @param n the node to process
   * @param context a visitor-specific context
   */
  public final void visit(final CAstNode n, Context context, CAstVisitor visitor) {
    if (visitor.enterNode(n, context, visitor))
      return;

    int NT = n.getKind();
    switch (NT) {
    case CAstNode.FUNCTION_EXPR: {
      if (visitor.visitFunctionExpr(n, context, visitor))
        break;
      visitor.leaveFunctionExpr(n, context, visitor);
      break;
    }

    case CAstNode.FUNCTION_STMT: {
      if (visitor.visitFunctionStmt(n, context, visitor))
        break;
      visitor.leaveFunctionStmt(n, context, visitor);
      break;
    }

    case CAstNode.LOCAL_SCOPE: {
      if (visitor.visitLocalScope(n, context, visitor))
        break;
      Context localContext = visitor.makeLocalContext(context, n);
      visitor.visit(n.getChild(0), localContext, visitor);
      visitor.leaveLocalScope(n, context, visitor);
      break;
    }

    case CAstNode.BLOCK_EXPR: {
      if (visitor.visitBlockExpr(n, context, visitor))
        break;
      visitor.visitAllChildren(n, context, visitor);
      visitor.leaveBlockExpr(n, context, visitor);
      break;
    }

    case CAstNode.BLOCK_STMT: {
      if (visitor.visitBlockStmt(n, context, visitor))
        break;
      visitor.visitAllChildren(n, context, visitor);
      visitor.leaveBlockStmt(n, context, visitor);
      break;
    }

    case CAstNode.LOOP: {
      if (visitor.visitLoop(n, context, visitor))
        break;
      visitor.visit(n.getChild(0), context, visitor);
      visitor.leaveLoopHeader(n, context, visitor);
      visitor.visit(n.getChild(1), context, visitor);
      visitor.leaveLoop(n, context, visitor);
      break;
    }

    case CAstNode.GET_CAUGHT_EXCEPTION: {
      if (visitor.visitGetCaughtException(n, context, visitor))
        break;
      visitor.leaveGetCaughtException(n, context, visitor);
      break;
    }

    case CAstNode.THIS: {
      if (visitor.visitThis(n, context, visitor))
        break;
      visitor.leaveThis(n, context, visitor);
      break;
    }

    case CAstNode.SUPER: {
      if (visitor.visitSuper(n, context, visitor))
        break;
      visitor.leaveSuper(n, context, visitor);
      break;
    }

    case CAstNode.CALL: {
      if (visitor.visitCall(n, context, visitor))
        break;
      visitor.visit(n.getChild(0), context, visitor);
      visitor.visitChildren(n, 2, context, visitor);
      visitor.leaveCall(n, context, visitor);
      break;
    }

    case CAstNode.VAR: {
      if (visitor.visitVar(n, context, visitor))
        break;
      visitor.leaveVar(n, context, visitor);
      break;
    }

    case CAstNode.CONSTANT: {
      if (visitor.visitConstant(n, context, visitor))
        break;
      visitor.leaveConstant(n, context, visitor);
      break;
    }

    case CAstNode.BINARY_EXPR: {
      if (visitor.visitBinaryExpr(n, context, visitor))
        break;
      visitor.visit(n.getChild(1), context, visitor);
      visitor.visit(n.getChild(2), context, visitor);
      visitor.leaveBinaryExpr(n, context, visitor);
      break;
    }

    case CAstNode.UNARY_EXPR: {
      if (visitor.visitUnaryExpr(n, context, visitor))
        break;
      visitor.visit(n.getChild(1), context, visitor);
      visitor.leaveUnaryExpr(n, context, visitor);
      break;
    }

    case CAstNode.ARRAY_LENGTH: {
      if (visitor.visitArrayLength(n, context, visitor))
        break;
      visitor.visit(n.getChild(0), context, visitor);
      visitor.leaveArrayLength(n, context, visitor);
      break;
    }

    case CAstNode.ARRAY_REF: {
      if (visitor.visitArrayRef(n, context, visitor))
        break;
      visitor.visit(n.getChild(0), context, visitor);
      visitor.visitChildren(n, 2, context, visitor);
      visitor.leaveArrayRef(n, context, visitor);
      break;
    }

    case CAstNode.DECL_STMT: {
      if (visitor.visitDeclStmt(n, context, visitor))
        break;
      if (n.getChildCount() == 4)
        visitor.visit(n.getChild(3), context, visitor);
      visitor.leaveDeclStmt(n, context, visitor);
      break;
    }

    case CAstNode.RETURN: {
      if (visitor.visitReturn(n, context, visitor))
        break;
      if (n.getChildCount() > 0)
        visitor.visit(n.getChild(0), context, visitor);
      visitor.leaveReturn(n, context, visitor);
      break;
    }

    case CAstNode.IFGOTO: {
      if (visitor.visitIfgoto(n, context, visitor))
        break;
      if (n.getChildCount() == 1) {
	visitor.visit(n.getChild(0), context, visitor);
      } else if (n.getChildCount() == 3) {
	visitor.visit(n.getChild(1), context, visitor);
	visitor.visit(n.getChild(2), context, visitor);
      } else {
	Assertions.UNREACHABLE();
      }

      visitor.leaveIfgoto(n, context, visitor);
      break;
    }

    case CAstNode.GOTO: {
      if (visitor.visitGoto(n, context, visitor))
        break;
      visitor.leaveGoto(n, context, visitor);
      break;
    }

    case CAstNode.LABEL_STMT: {
      if (visitor.visitLabelStmt(n, context, visitor))
        break;
      visitor.visit(n.getChild(0), context, visitor);
      if (n.getChildCount() == 2)
        visitor.visit(n.getChild(1), context, visitor);
      else // FIXME: this doesn't belong here
        Assertions._assert(n.getChildCount() < 2);
      visitor.leaveLabelStmt(n, context, visitor);
      break;
    }

    case CAstNode.IF_STMT: {
      if (visitor.visitIfStmt(n, context, visitor))
        break;
      visitor.visit(n.getChild(0), context, visitor);
      visitor.leaveIfStmtCondition(n, context, visitor);
      visitor.visit(n.getChild(1), context, visitor);
      visitor.leaveIfStmtTrueClause(n, context, visitor);
      if (n.getChildCount() == 3)
        visitor.visit(n.getChild(2), context, visitor);
      visitor.leaveIfStmt(n, context, visitor);
      break;
    }

    case CAstNode.IF_EXPR: {
      if (visitor.visitIfExpr(n, context, visitor))
        break;
      visitor.visit(n.getChild(0), context, visitor);
      visitor.leaveIfExprCondition(n, context, visitor);
      visitor.visit(n.getChild(1), context, visitor);
      visitor.leaveIfExprTrueClause(n, context, visitor);
      if (n.getChildCount() == 3)
        visitor.visit(n.getChild(2), context, visitor);
      visitor.leaveIfExpr(n, context, visitor);
      break;
    }

    case CAstNode.NEW: {
      if (visitor.visitNew(n, context, visitor))
        break;

      for(int i = 1; i < n.getChildCount(); i++) {
	visitor.visit(n.getChild(i), context, visitor);
      }	  

      visitor.leaveNew(n, context, visitor);
      break;
    }

    case CAstNode.OBJECT_LITERAL: {
      if (visitor.visitObjectLiteral(n, context, visitor))
        break;
      visitor.visit(n.getChild(0), context, visitor);
      for (int i = 1; i < n.getChildCount(); i+=2) {
        visitor.visit(n.getChild(i), context, visitor);
        visitor.visit(n.getChild(i+1), context, visitor);
        visitor.leaveObjectLiteralFieldInit(n, i, context, visitor);
      }
      visitor.leaveObjectLiteral(n, context, visitor);
      break;
    }

    case CAstNode.ARRAY_LITERAL: {
      if (visitor.visitArrayLiteral(n, context, visitor))
        break;
      visitor.visit(n.getChild(0), context, visitor);
      visitor.leaveArrayLiteralObject(n, context, visitor);
      for (int i = 1; i < n.getChildCount(); i++) {
        visitor.visit(n.getChild(i), context, visitor);
        visitor.leaveArrayLiteralInitElement(n, i, context, visitor);
      }
      visitor.leaveArrayLiteral(n, context, visitor);
      break;
    }

    case CAstNode.OBJECT_REF: {
      if (visitor.visitObjectRef(n, context, visitor))
        break;
      visitor.visit(n.getChild(0), context, visitor);
      visitor.leaveObjectRef(n, context, visitor);
      break;
    }

    case CAstNode.ASSIGN:
    case CAstNode.ASSIGN_PRE_OP:
    case CAstNode.ASSIGN_POST_OP: {
      if (visitor.visitAssign(n, context, visitor))
        break;
      visitor.visit(n.getChild(1), context, visitor);
      // TODO: is this correct?
      if (visitor.visitAssignNodes(n.getChild(0), context, n.getChild(1), n, visitor))
        break;
      visitor.leaveAssign(n, context, visitor);
      break;
    }

    case CAstNode.SWITCH: {
      if (visitor.visitSwitch(n, context, visitor))
        break;
      visitor.visit(n.getChild(0), context, visitor);
      visitor.leaveSwitchValue(n, context, visitor);
      visitor.visit(n.getChild(1), context, visitor);
      visitor.leaveSwitch(n, context, visitor);
      break;
    }

    case CAstNode.THROW: {
      if (visitor.visitThrow(n, context, visitor))
        break;
      visitor.visit(n.getChild(0), context, visitor);
      visitor.leaveThrow(n, context, visitor);
      break;
    }

    case CAstNode.CATCH: {
      if (visitor.visitCatch(n, context, visitor))
        break;
      visitor.visitChildren(n, 1, context, visitor);
      visitor.leaveCatch(n, context, visitor);
      break;
    }

    case CAstNode.UNWIND: {
      if (visitor.visitUnwind(n, context, visitor))
        break;
      Context unwindContext = visitor.makeUnwindContext(context, n.getChild(1), visitor);
      visitor.visit(n.getChild(0), unwindContext, visitor);
      visitor.visit(n.getChild(1), context, visitor);
      visitor.leaveUnwind(n, context, visitor);
      break;
    }

    case CAstNode.TRY: {
      if (visitor.visitTry(n, context, visitor))
        break;
      visitor.visit(n.getChild(0), context, visitor);
      visitor.leaveTryBlock(n, context, visitor);
      visitor.visit(n.getChild(1), context, visitor);
      visitor.leaveTry(n, context, visitor);
      break;
    }

    case CAstNode.EMPTY: {
      if (visitor.visitEmpty(n, context, visitor))
        break;
      visitor.leaveEmpty(n, context, visitor);
      break;
    }

    case CAstNode.PRIMITIVE: {
      if (visitor.visitPrimitive(n, context, visitor))
        break;
      visitor.leavePrimitive(n, context, visitor);
      break;
    }

    case CAstNode.VOID: {
      if (visitor.visitVoid(n, context, visitor))
        break;
      visitor.leaveVoid(n, context, visitor);
      break;
    }

    case CAstNode.CAST: {
      if (visitor.visitCast(n, context, visitor))
        break;
      visitor.visit(n.getChild(1), context, visitor);
      visitor.leaveCast(n, context, visitor);
      break;
    }

    case CAstNode.INSTANCEOF: {
      if (visitor.visitInstanceOf(n, context, visitor))
        break;
      visitor.visit(n.getChild(1), context, visitor);
      visitor.leaveInstanceOf(n, context, visitor);
      break;
    }

    case CAstNode.ASSERT: {
      if (visitor.visitAssert(n, context, visitor))
	break;
      visitor.visit(n.getChild(0), context, visitor);
      visitor.leaveAssert(n, context, visitor);
      break;	
    }
    
    case CAstNode.EACH_ELEMENT_GET: {
      if (visitor.visitEachElementGet(n, context, visitor))
	break;
      visitor.visit(n.getChild(0), context, visitor);
      visitor.leaveEachElementGet(n, context, visitor);
      break;	
    }
    
    case CAstNode.EACH_ELEMENT_HAS_NEXT: {
      if (visitor.visitEachElementHasNext(n, context, visitor))
	break;
      visitor.visit(n.getChild(0), context, visitor);
      visitor.leaveEachElementHasNext(n, context, visitor);
      break;	
    }

    case CAstNode.TYPE_LITERAL_EXPR: {
      if (visitor.visitTypeLiteralExpr(n, context, visitor)) {
	break;
      }
      visitor.visit(n.getChild(0), context, visitor);
      visitor.leaveTypeLiteralExpr(n, context, visitor);
      break;
    }

    default: {
      if (!visitor.doVisit(n, context, visitor)) {
        Trace.println("looking at unhandled " + n + "(" + NT + ")" + " of " + n.getClass());
        Assertions.UNREACHABLE("cannot handle node of kind " + NT);
      }
    }
    }

    if (context != null) {
      visitor.visitScopedEntities(context.top(), context.top().getScopedEntities(n), context, visitor);
    }

    visitor.postProcessNode(n, context, visitor);
  }

  protected boolean visitAssignNodes(CAstNode n, Context context, CAstNode v, CAstNode a, CAstVisitor visitor) {
    int NT = a.getKind();
    boolean assign = NT == CAstNode.ASSIGN;
    boolean preOp = NT == CAstNode.ASSIGN_PRE_OP;
    switch (n.getKind()) {
    case CAstNode.ARRAY_REF: {
      if (assign ? visitor.visitArrayRefAssign(n, v, a, context, visitor)
                 : visitor.visitArrayRefAssignOp(n, v, a, preOp, context, visitor))
        return true;
      visitor.visit(n.getChild(0), context, visitor);
      // XXX: we don't really need to visit array dims twice!
      visitor.visitChildren(n, 2, context, visitor);
      if (assign)
        visitor.leaveArrayRefAssign(n, v, a, context, visitor);
      else
        visitor.leaveArrayRefAssignOp(n, v, a, preOp, context, visitor);
      break;
    }

    case CAstNode.OBJECT_REF: {
      if (assign ? visitor.visitObjectRefAssign(n, v, a, context, visitor)
                 : visitor.visitObjectRefAssignOp(n, v, a, preOp, context, visitor))
        return true;
      visitor.visit(n.getChild(0), context, visitor);
      if (assign)
        visitor.leaveObjectRefAssign(n, v, a, context, visitor);
      else
        visitor.leaveObjectRefAssignOp(n, v, a, preOp, context, visitor);
      break;
    }

    case CAstNode.BLOCK_EXPR: {
      if (assign ? visitor.visitBlockExprAssign(n, v, a, context, visitor)
                 : visitor.visitBlockExprAssignOp(n, v, a, preOp, context, visitor))
        return true;
      // FIXME: is it correct to ignore all the other children?
      if (visitor.visitAssignNodes(n.getChild(n.getChildCount() - 1), context, v, a, visitor))
        return true;
      if (assign)
        visitor.leaveBlockExprAssign(n, v, a, context, visitor);
      else
        visitor.leaveBlockExprAssignOp(n, v, a, preOp, context, visitor);
      break;
    }

    case CAstNode.VAR: {
      if (assign ? visitor.visitVarAssign(n, v, a, context, visitor)
                 : visitor.visitVarAssignOp(n, v, a, preOp, context, visitor))
        return true;
      if (assign)
        visitor.leaveVarAssign(n, v, a, context, visitor);
      else
        visitor.leaveVarAssignOp(n, v, a, preOp, context, visitor);
      break;
    }

    default: {
      if (!visitor.doVisitAssignNodes(n, context, v, a, visitor)) {
	Trace.println("cannot handle assign to kind " + n.getKind());
	throw new UnsupportedOperationException(
	  "cannot handle assignment: " + 
	  CAstPrinter.print(a, context.top().getSourceMap()));
      }
    }
    }
    return false;
  }

  /**
   * Enter the node visitor.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean enterNode(CAstNode n, Context c, CAstVisitor visitor) { return false; }
  /**
   * Post-process a node after visiting it.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void postProcessNode(CAstNode n, Context c, CAstVisitor visitor) { return; }

  /**
   * Visit any node.  Override only this to change behavior for all nodes.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  public boolean visitNode(CAstNode n, Context c, CAstVisitor visitor) { return false; }
  /**
   * Leave any node.  Override only this to change behavior for all nodes.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  public void leaveNode(CAstNode n, Context c, CAstVisitor visitor) { return; }

  /**
   * Visit a FunctionExpr node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitFunctionExpr(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave a FunctionExpr node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveFunctionExpr(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit a FunctionStmt node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitFunctionStmt(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave a FunctionStmt node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveFunctionStmt(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit a LocalScope node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitLocalScope(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave a LocalScope node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveLocalScope(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit a BlockExpr node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitBlockExpr(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave a BlockExpr node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveBlockExpr(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit a BlockStmt node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitBlockStmt(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave a BlockStmt node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveBlockStmt(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit a Loop node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitLoop(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Visit a Loop node after processing the loop header.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveLoopHeader(CAstNode n, Context c, CAstVisitor visitor) { /* empty */ }
  /**
   * Leave a Loop node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveLoop(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit a GetCaughtException node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitGetCaughtException(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave a GetCaughtException node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveGetCaughtException(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit a This node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitThis(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave a This node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveThis(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit a Super node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitSuper(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave a Super node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveSuper(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit a Call node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitCall(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave a Call node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveCall(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit a Var node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitVar(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave a Var node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveVar(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit a Constant node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitConstant(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave a Constant node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveConstant(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit a BinaryExpr node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitBinaryExpr(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave a BinaryExpr node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveBinaryExpr(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit a UnaryExpr node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitUnaryExpr(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave a UnaryExpr node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveUnaryExpr(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit an ArrayLength node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitArrayLength(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave an ArrayLength node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveArrayLength(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit an ArrayRef node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitArrayRef(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave an ArrayRef node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveArrayRef(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit a DeclStmt node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitDeclStmt(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave a DeclStmt node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveDeclStmt(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit a Return node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitReturn(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave a Return node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveReturn(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit an Ifgoto node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitIfgoto(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave an Ifgoto node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveIfgoto(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit a Goto node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitGoto(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave a Goto node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveGoto(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit a LabelStmt node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitLabelStmt(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave a LabelStmt node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveLabelStmt(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit an IfStmt node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitIfStmt(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Visit an IfStmt node after processing the condition.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveIfStmtCondition(CAstNode n, Context c, CAstVisitor visitor) { /* empty */ }
  /**
   * Visit an IfStmt node after processing the true clause.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveIfStmtTrueClause(CAstNode n, Context c, CAstVisitor visitor) { /* empty */ }
  /**
   * Leave an IfStmt node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveIfStmt(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit an IfExpr node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitIfExpr(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Visit an IfExpr node after processing the condition.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveIfExprCondition(CAstNode n, Context c, CAstVisitor visitor) { /* empty */ }
  /**
   * Visit an IfExpr node after processing the true clause.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveIfExprTrueClause(CAstNode n, Context c, CAstVisitor visitor) { /* empty */ }
  /**
   * Leave an IfExpr node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveIfExpr(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit a New node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitNew(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave a New node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveNew(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit an ObjectLiteral node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitObjectLiteral(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Visit an ObjectLiteral node after processing the {i}th field initializer.
   * @param n the node to process
   * @param i the field position that was initialized
   * @param c a visitor-specific context
   */
  protected void leaveObjectLiteralFieldInit(CAstNode n, int i, Context c, CAstVisitor visitor) { /* empty */ }
  /**
   * Leave an ObjectLiteral node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveObjectLiteral(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit an ArrayLiteral node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitArrayLiteral(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Visit an ArrayLiteral node after processing the array object.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveArrayLiteralObject(CAstNode n, Context c, CAstVisitor visitor) { /* empty */ }
  /**
   * Visit an ArrayLiteral node after processing the {i}th element initializer.
   * @param n the node to process
   * @param i the index that was initialized
   * @param c a visitor-specific context
   */
  protected void leaveArrayLiteralInitElement(CAstNode n, int i, Context c, CAstVisitor visitor) { /* empty */ }
  /**
   * Leave a ArrayLiteral node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveArrayLiteral(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit an ObjectRef node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitObjectRef(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave an ObjectRef node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveObjectRef(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit an Assign node.  Override only this to change behavior for all assignment nodes.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  public boolean visitAssign(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave an Assign node.  Override only this to change behavior for all assignment nodes.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  public void leaveAssign(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit an ArrayRef Assignment node after visiting the RHS.
   * @param n the LHS node to process
   * @param v the RHS node to process
   * @param a the assignment node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitArrayRefAssign(CAstNode n, CAstNode v, CAstNode a, Context c, CAstVisitor visitor) { /* empty */ return false; }
  /**
   * Visit an ArrayRef Assignment node after visiting the LHS.
   * @param n the LHS node to process
   * @param v the RHS node to process
   * @param a the assignment node to process
   * @param c a visitor-specific context
   */
  protected void leaveArrayRefAssign(CAstNode n, CAstNode v, CAstNode a, Context c, CAstVisitor visitor) { /* empty */ }
  /**
   * Visit an ArrayRef Op/Assignment node after visiting the RHS.
   * @param n the LHS node to process
   * @param v the RHS node to process
   * @param a the assignment node to process
   * @param pre whether the value before the operation should be used
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitArrayRefAssignOp(CAstNode n, CAstNode v, CAstNode a, boolean pre, Context c, CAstVisitor visitor) { /* empty */ return false; }
  /**
   * Visit an ArrayRef Op/Assignment node after visiting the LHS.
   * @param n the LHS node to process
   * @param v the RHS node to process
   * @param a the assignment node to process
   * @param pre whether the value before the operation should be used
   * @param c a visitor-specific context
   */
  protected void leaveArrayRefAssignOp(CAstNode n, CAstNode v, CAstNode a, boolean pre, Context c, CAstVisitor visitor) { /* empty */ }
  /**
   * Visit an ObjectRef Assignment node after visiting the RHS.
   * @param n the LHS node to process
   * @param v the RHS node to process
   * @param a the assignment node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitObjectRefAssign(CAstNode n, CAstNode v, CAstNode a, Context c, CAstVisitor visitor) { /* empty */ return false; }
  /**
   * Visit an ObjectRef Assignment node after visiting the LHS.
   * @param n the LHS node to process
   * @param v the RHS node to process
   * @param a the assignment node to process
   * @param c a visitor-specific context
   */
  protected void leaveObjectRefAssign(CAstNode n, CAstNode v, CAstNode a, Context c, CAstVisitor visitor) { /* empty */ }
  /**
   * Visit an ObjectRef Op/Assignment node after visiting the RHS.
   * @param n the LHS node to process
   * @param v the RHS node to process
   * @param a the assignment node to process
   * @param pre whether the value before the operation should be used
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitObjectRefAssignOp(CAstNode n, CAstNode v, CAstNode a, boolean pre, Context c, CAstVisitor visitor) { /* empty */ return false; }
  /**
   * Visit an ObjectRef Op/Assignment node after visiting the LHS.
   * @param n the LHS node to process
   * @param v the RHS node to process
   * @param a the assignment node to process
   * @param pre whether the value before the operation should be used
   * @param c a visitor-specific context
   */
  protected void leaveObjectRefAssignOp(CAstNode n, CAstNode v, CAstNode a, boolean pre, Context c, CAstVisitor visitor) { /* empty */ }
  /**
   * Visit a BlockExpr Assignment node after visiting the RHS.
   * @param n the LHS node to process
   * @param v the RHS node to process
   * @param a the assignment node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitBlockExprAssign(CAstNode n, CAstNode v, CAstNode a, Context c, CAstVisitor visitor) { /* empty */ return false; }
  /**
   * Visit a BlockExpr Assignment node after visiting the LHS.
   * @param n the LHS node to process
   * @param v the RHS node to process
   * @param a the assignment node to process
   * @param c a visitor-specific context
   */
  protected void leaveBlockExprAssign(CAstNode n, CAstNode v, CAstNode a, Context c, CAstVisitor visitor) { /* empty */ }
  /**
   * Visit a BlockExpr Op/Assignment node after visiting the RHS.
   * @param n the LHS node to process
   * @param v the RHS node to process
   * @param a the assignment node to process
   * @param pre whether the value before the operation should be used
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitBlockExprAssignOp(CAstNode n, CAstNode v, CAstNode a, boolean pre, Context c, CAstVisitor visitor) { /* empty */ return false; }
  /**
   * Visit a BlockExpr Op/Assignment node after visiting the LHS.
   * @param n the LHS node to process
   * @param v the RHS node to process
   * @param a the assignment node to process
   * @param pre whether the value before the operation should be used
   * @param c a visitor-specific context
   */
  protected void leaveBlockExprAssignOp(CAstNode n, CAstNode v, CAstNode a, boolean pre, Context c, CAstVisitor visitor) { /* empty */ }
  /**
   * Visit a Var Assignment node after visiting the RHS.
   * @param n the LHS node to process
   * @param v the RHS node to process
   * @param a the assignment node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitVarAssign(CAstNode n, CAstNode v, CAstNode a, Context c, CAstVisitor visitor) { /* empty */ return false; }
  /**
   * Visit a Var Assignment node after visiting the LHS.
   * @param n the LHS node to process
   * @param v the RHS node to process
   * @param a the assignment node to process
   * @param c a visitor-specific context
   */
  protected void leaveVarAssign(CAstNode n, CAstNode v, CAstNode a, Context c, CAstVisitor visitor) { /* empty */ }
  /**
   * Visit a Var Op/Assignment node after visiting the RHS.
   * @param n the LHS node to process
   * @param v the RHS node to process
   * @param a the assignment node to process
   * @param pre whether the value before the operation should be used
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitVarAssignOp(CAstNode n, CAstNode v, CAstNode a, boolean pre, Context c, CAstVisitor visitor) { /* empty */ return false; }
  /**
   * Visit a Var Op/Assignment node after visiting the LHS.
   * @param n the LHS node to process
   * @param v the RHS node to process
   * @param a the assignment node to process
   * @param pre whether the value before the operation should be used
   * @param c a visitor-specific context
   */
  protected void leaveVarAssignOp(CAstNode n, CAstNode v, CAstNode a, boolean pre, Context c, CAstVisitor visitor) { /* empty */ }
  /**
   * Visit a Switch node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitSwitch(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Visit a Switch node after processing the switch value.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveSwitchValue(CAstNode n, Context c, CAstVisitor visitor) { /* empty */ }
  /**
   * Leave a Switch node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveSwitch(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit a Throw node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitThrow(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave a Throw node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveThrow(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit a Catch node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitCatch(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave a Catch node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveCatch(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit an Unwind node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitUnwind(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave an Unwind node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveUnwind(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit a Try node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitTry(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Visit a Try node after processing the try block.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveTryBlock(CAstNode n, Context c, CAstVisitor visitor) { /* empty */ }
  /**
   * Leave a Try node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveTry(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit an Empty node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitEmpty(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave an Empty node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveEmpty(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit a Primitive node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitPrimitive(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave a Primitive node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leavePrimitive(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit a Void node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitVoid(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave a Void node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveVoid(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit a Cast node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitCast(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave a Cast node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveCast(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit an InstanceOf node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitInstanceOf(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave an InstanceOf node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveInstanceOf(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit an InstanceOf node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitAssert(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave an InstanceOf node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveAssert(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit an InstanceOf node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitEachElementHasNext(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave an InstanceOf node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveEachElementHasNext(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit an InstanceOf node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitEachElementGet(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave an FOR_EACH_ELEMENT_GET node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveEachElementGet(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
  /**
   * Visit an TYPE_LITERAL_EXPR node.
   * @param n the node to process
   * @param c a visitor-specific context
   * @return true if no further processing is needed
   */
  protected boolean visitTypeLiteralExpr(CAstNode n, Context c, CAstVisitor visitor) { return visitor.visitNode(n, c, visitor); }
  /**
   * Leave an TYPE_LITERAL_EXPR node.
   * @param n the node to process
   * @param c a visitor-specific context
   */
  protected void leaveTypeLiteralExpr(CAstNode n, Context c, CAstVisitor visitor) { visitor.leaveNode(n, c, visitor); }
}
