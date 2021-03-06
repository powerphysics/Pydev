/**
 * Copyright (c) 2005-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Eclipse Public License (EPL).
 * Please see the license.txt included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */
package org.python.pydev.parser.prettyprinterv2;

/**
 * Interface for the line part that marks an indent or dedent.
 */
public interface ILinePartIndentMark extends ILinePart{

    /**
     * @return true if a new line is required on the indent and false otherwise.
     * @note only applicable on indent.
     */
    public boolean getRequireNewLineOnIndent();
    
    public boolean isIndent();
}
