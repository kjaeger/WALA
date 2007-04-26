/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.logic;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.ibm.wala.util.collections.HashSetFactory;

public class FunctionTerm implements ITerm {

  private final List<ITerm> parameters;
  private final IFunction f;
  
  private FunctionTerm(IFunction f, List<ITerm> parameters) throws IllegalArgumentException {
    this.f = f;
    this.parameters = parameters;
    if (f == null) {
      throw new IllegalArgumentException("f cannot be null");
    }
  }
  
  public Kind getKind() {
    return Kind.FUNCTION;
  }

  @Override
  public String toString() {
    StringBuffer result = new StringBuffer(f.getSymbol());
    result.append("(");
    for (int i = 0; i < f.getNumberOfParameters() - 1; i++) {
      result.append(parameters.get(i));
      result.append(",");
    }
    result.append(parameters.get(f.getNumberOfParameters() - 1));
    result.append(")");
    return result.toString();
  }

  public static FunctionTerm make(UnaryFunction f, int i) {
    List<ITerm> p = new LinkedList<ITerm>();
    p.add(IntConstant.make(i));
    return new FunctionTerm(f, p);
  }
  
  public static FunctionTerm make(BinaryFunction f, int i, int j) {
    List<ITerm> p = new LinkedList<ITerm>();
    p.add(IntConstant.make(i));
    p.add(IntConstant.make(j));
    return new FunctionTerm(f, p);
  }
  

  public static ITerm make(IFunction f, List<ITerm> terms) {
    return new FunctionTerm(f, terms);
  }
  
  public static FunctionTerm make(BinaryFunction f, ITerm i, int j) {
    List<ITerm> p = new LinkedList<ITerm>();
    p.add(i);
    p.add(IntConstant.make(j));
    return new FunctionTerm(f, p);
  }
  
  public static FunctionTerm make(BinaryFunction f, ITerm i, ITerm j) {
    List<ITerm> p = new LinkedList<ITerm>();
    p.add(i);
    p.add(j);
    return new FunctionTerm(f, p);
  }
  
  public static FunctionTerm make(UnaryFunction f, ITerm t) {
    List<ITerm> p = new LinkedList<ITerm>();
    p.add(t);
    return new FunctionTerm(f, p);
  }

  public IFunction getFunction() {
    return f;
  }

  public List<ITerm> getParameters() {
    return Collections.unmodifiableList(parameters);
  }

  public Collection<Variable> getFreeVariables() {
    Collection<Variable> result = HashSetFactory.make();
    for (ITerm t : parameters) {
      result.addAll(t.getFreeVariables());
    }
    return result;
  }

  @Override
  public int hashCode() {
    final int PRIME = 31;
    int result = 1;
    result = PRIME * result + ((f == null) ? 0 : f.hashCode());
    result = PRIME * result + ((parameters == null) ? 0 : parameters.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    final FunctionTerm other = (FunctionTerm) obj;
    if (f == null) {
      if (other.f != null)
        return false;
    } else if (!f.equals(other.f))
      return false;
    if (parameters == null) {
      if (other.parameters != null)
        return false;
    } else if (!parameters.equals(other.parameters))
      return false;
    return true;
  }


}
