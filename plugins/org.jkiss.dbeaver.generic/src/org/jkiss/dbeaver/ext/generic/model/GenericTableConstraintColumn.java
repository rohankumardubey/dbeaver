/*
 * Copyright (C) 2010-2012 Serge Rieder
 * serge@jkiss.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.jkiss.dbeaver.ext.generic.model;

import org.jkiss.dbeaver.model.impl.jdbc.struct.JDBCTableConstraint;
import org.jkiss.dbeaver.model.impl.struct.AbstractTableConstraintColumn;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSTableConstraint;
import org.jkiss.dbeaver.model.struct.DBSObject;

/**
 * GenericTableConstraintColumn
 */
public class GenericTableConstraintColumn extends AbstractTableConstraintColumn
{
    private JDBCTableConstraint constraint;
    private GenericTableColumn tableColumn;
    private int ordinalPosition;

    public GenericTableConstraintColumn(JDBCTableConstraint constraint, GenericTableColumn tableColumn, int ordinalPosition)
    {
        this.constraint = constraint;
        this.tableColumn = tableColumn;
        this.ordinalPosition = ordinalPosition;
    }

    public GenericTableConstraintColumn(GenericTableConstraint constraint, GenericTableConstraintColumn column)
    {
        this.constraint = constraint;
        this.tableColumn = column.tableColumn;
        this.ordinalPosition = column.ordinalPosition;
    }

    @Override
    public String getName()
    {
        return tableColumn.getName();
    }

    @Override
    @Property(id = "name", name = "Column", viewable = true, order = 1)
    public GenericTableColumn getAttribute()
    {
        return tableColumn;
    }

    @Override
    @Property(name = "Position", viewable = false, order = 2)
    public int getOrdinalPosition()
    {
        return ordinalPosition;
    }

//    @Property(name = "Description", viewable = true, order = 100)
    @Override
    public String getDescription()
    {
        return tableColumn.getDescription();
    }

    @Override
    public JDBCTableConstraint getParentObject()
    {
        return constraint;
    }

    @Override
    public GenericDataSource getDataSource()
    {
        return (GenericDataSource) constraint.getDataSource();
    }

}
