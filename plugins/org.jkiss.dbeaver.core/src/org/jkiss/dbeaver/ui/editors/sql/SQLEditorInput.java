/*
 * Copyright (c) 2011, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.ui.editors.sql;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IPersistableElement;
import org.jkiss.dbeaver.core.DBeaverCore;
import org.jkiss.dbeaver.ext.IAutoSaveEditorInput;
import org.jkiss.dbeaver.ext.IDatabaseEditorInput;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.navigator.DBNDatabaseNode;
import org.jkiss.dbeaver.model.struct.DBSDataSourceContainer;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.ui.editors.ProjectFileEditorInput;

/**
 * SQLEditorInput
 */
public class SQLEditorInput extends ProjectFileEditorInput implements IPersistableElement, IAutoSaveEditorInput, IDatabaseEditorInput
{
    private DBSDataSourceContainer dataSourceContainer;
    private String scriptName;

    public SQLEditorInput(IFile file, DBSDataSourceContainer dataSourceContainer)
    {
        super(file);
        this.dataSourceContainer = dataSourceContainer;
        this.scriptName = file.getName();
    }

    public IProject getProject()
    {
        return getFile().getProject();
    }

    public DBSDataSourceContainer getDataSourceContainer()
    {
        return dataSourceContainer;
    }

    public void setDataSourceContainer(DBSDataSourceContainer container)
    {
        dataSourceContainer = container;
    }

    public String getScriptName()
    {
        return scriptName;
    }

    public String getName()
    {
        if (dataSourceContainer == null) {
            return "<None> - " + scriptName;
        }
        return dataSourceContainer.getName() + " - " + scriptName;
    }

    public String getToolTipText()
    {
        if (dataSourceContainer == null) {
            return super.getName();
        }
        return
            "Connection: " + dataSourceContainer.getName() + "\n" +
            "Type: " + dataSourceContainer.getDriver().getName() + "\n" +
            "URL: " + dataSourceContainer.getConnectionInfo().getUrl();
    }

    @Override
    public IPersistableElement getPersistable()
    {
        return this;
    }

    public String getFactoryId()
    {
        return SQLEditorInputFactory.getFactoryId();
    }

    public void saveState(IMemento memento) 
    {
        SQLEditorInputFactory.saveState(memento, this);
    }

    public boolean isAutoSaveEnabled()
    {
        return true;
    }

    public DBNDatabaseNode getTreeNode()
    {
        return DBeaverCore.getInstance().getNavigatorModel().findNode(getDataSourceContainer());
    }

    public DBSObject getDatabaseObject()
    {
        return getDataSourceContainer();
    }

    public String getDefaultPageId()
    {
        return null;
    }

    public DBPDataSource getDataSource()
    {
        return getDataSourceContainer().getDataSource();
    }

}
