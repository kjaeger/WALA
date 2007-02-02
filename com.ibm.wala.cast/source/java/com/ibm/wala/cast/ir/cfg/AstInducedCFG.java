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
package com.ibm.wala.cast.ir.cfg;

import com.ibm.wala.cast.ir.ssa.*;
import com.ibm.wala.cfg.*;
import com.ibm.wala.classLoader.*;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ssa.*;

public class AstInducedCFG extends InducedCFG {

  public AstInducedCFG(SSAInstruction[] instructions, IMethod method, Context context) {
    super(instructions, method, context);
  }

  protected class AstPEIVisitor extends PEIVisitor implements AstInstructionVisitor {

    protected AstPEIVisitor(boolean[] r) {
      super(r);
    }

    public void visitAstLexicalRead(AstLexicalRead inst) {
    }

    public void visitAstLexicalWrite(AstLexicalWrite inst) {
    }

    public void visitAstGlobalRead(AstGlobalRead instruction) {
    }

    public void visitAstGlobalWrite(AstGlobalWrite instruction) {
    }

    public void visitNonExceptingThrow(NonExceptingThrowInstruction inst) {
      breakBasicBlock();
    }
    
    public void visitAssert(AstAssertInstruction instruction) {
	
    }

    public void visitEachElementHasNext(EachElementHasNextInstruction inst) {

    }

    public void visitEachElementGet(EachElementGetInstruction inst) {

    }
  }
    
  protected class AstBranchVisitor extends BranchVisitor implements AstInstructionVisitor {

    protected AstBranchVisitor(boolean[] r) {
      super(r);
    }

    public void visitAstLexicalRead(AstLexicalRead inst) {
    }

    public void visitAstLexicalWrite(AstLexicalWrite inst) {
    }

    public void visitAstGlobalRead(AstGlobalRead instruction) {
    }
    
    public void visitAstGlobalWrite(AstGlobalWrite instruction) {
    }

    public void visitNonExceptingThrow(NonExceptingThrowInstruction inst) {
    }
	
    public void visitAssert(AstAssertInstruction instruction) {
    }

    public void visitEachElementHasNext(EachElementHasNextInstruction inst) {
    }

    public void visitEachElementGet(EachElementGetInstruction inst) {
    }

  }
    
  protected BranchVisitor makeBranchVisitor(boolean[] r) {
    return new AstBranchVisitor(r);
  }

  protected PEIVisitor makePEIVisitor(boolean[] r) {
    return new AstPEIVisitor(r);
  }

}
