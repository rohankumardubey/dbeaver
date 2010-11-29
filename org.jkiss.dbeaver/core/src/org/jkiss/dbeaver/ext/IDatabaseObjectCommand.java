/*
 * Copyright (c) 2010, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.ext;

import org.eclipse.swt.graphics.Image;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * Object change command
 */
public interface IDatabaseObjectCommand<OBJECT_TYPE extends DBSObject> {

    public static final long FLAG_NONE = 0;
    public static final long FLAG_PERMANENT = 1;

    public enum MergeResult {
        NONE,
        CANCEL_PREVIOUS,
        CANCEL_BOTH,
        ABSORBED
    }

    String getTitle();

    Image getIcon();

    long getFlags();

    void updateModel(OBJECT_TYPE object, boolean undo);

    MergeResult merge(IDatabaseObjectCommand<OBJECT_TYPE> prevCommand, boolean undo);

    IDatabasePersistAction[] getPersistActions();

}
