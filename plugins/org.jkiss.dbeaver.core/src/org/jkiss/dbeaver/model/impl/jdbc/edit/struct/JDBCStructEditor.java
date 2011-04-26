/*
 * Copyright (c) 2011, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.model.impl.jdbc.edit.struct;

import org.jkiss.dbeaver.model.edit.DBECommandQueue;
import org.jkiss.dbeaver.model.edit.DBEStructEditor;
import org.jkiss.dbeaver.model.edit.DBEStructHandler;
import org.jkiss.dbeaver.model.impl.jdbc.struct.JDBCTable;
import org.jkiss.dbeaver.model.struct.DBSEntity;

/**
 * JDBC struct editor
 */
public abstract class JDBCStructEditor<OBJECT_TYPE extends DBSEntity>
    extends JDBCObjectEditor<OBJECT_TYPE>
    implements DBEStructEditor<OBJECT_TYPE>
{

    public DBEStructHandler<OBJECT_TYPE> makeStructHandler(DBECommandQueue queue)
    {
        return null;
    }

}

