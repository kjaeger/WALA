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
package com.ibm.wala.cast.tree.impl;

import com.ibm.wala.cast.tree.*;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.*;

public abstract class AbstractSourcePosition implements Position {
    
  public boolean equals(Object o){
    if (o instanceof Position) {
      Position p = (Position)o;
      return getFirstLine() == p.getFirstLine() &&
             getLastLine() == p.getLastLine() &&
	     getFirstCol() == p.getFirstCol() &&
	     getLastCol() == p.getLastCol() &&
	     ( (getURL() != null)?
	       getURL().equals(p.getURL()):
	       p.getURL() == null);
    } else {
      return false;
    }
  }

  public int hashCode() { 
    return getFirstLine()*getLastLine()*getFirstCol()*getLastCol();
  }

  public int compareTo(Object o) {
    if (o instanceof Position) {
      Position p = (Position)o;
      if (getFirstLine() != p.getFirstLine()) {
	return getFirstLine() - p.getFirstLine();
      } else if (getFirstCol() != p.getFirstCol()) {
	return getFirstCol() - p.getFirstCol();
      } else if (getLastLine() != p.getLastLine()) {
	return getLastLine() - p.getLastLine();
      } else {
	return getLastCol() - p.getLastCol();
      }
    } else {
      return 0;
    }
  }

  public String toString() {
    return "["+getFirstLine()+":"+getFirstCol()+"] -> ["+getLastLine()+":"+getLastCol()+"]";
  }

}
