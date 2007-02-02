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
package com.ibm.wala.cast.ipa.callgraph;

import com.ibm.wala.ipa.callgraph.propagation.AbstractFieldPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;

public abstract class ReflectedFieldPointerKey extends AbstractFieldPointerKey {

  ReflectedFieldPointerKey(InstanceKey container) {
    super(container);
  }

  public abstract Object getFieldIdentifier();

  private static final Object arrayStateKey = new Object() {
    public String toString() { return "ArrayStateKey"; }
  };

  public boolean equals(Object obj) {
    if (obj instanceof ReflectedFieldPointerKey) {
      ReflectedFieldPointerKey other = (ReflectedFieldPointerKey) obj;
      return
	getFieldIdentifier().equals(other.getFieldIdentifier()) &&
	getInstanceKey().equals(other.getInstanceKey());
    } else {
      return false;
    }
  }

  public int hashCode() {
    return getFieldIdentifier().hashCode();
  }

  public String toString() { return "field:" + getFieldIdentifier(); }

  public static ReflectedFieldPointerKey literal(final String lit, InstanceKey instance) {
    return new ReflectedFieldPointerKey(instance) {
      public Object getFieldIdentifier() {
	return lit;
      }
    };   
  }

  public static ReflectedFieldPointerKey mapped(final InstanceKey mapFrom, InstanceKey instance) {
    return new ReflectedFieldPointerKey(instance) {
      public Object getFieldIdentifier() {
	return mapFrom;
      }
    };   
  }

  public static ReflectedFieldPointerKey index(InstanceKey instance) {
    return new ReflectedFieldPointerKey(instance) {
      public Object getFieldIdentifier() {
	return arrayStateKey;
      }
    };   
  }

}
