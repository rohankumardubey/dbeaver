/*
 * Copyright (c) 2011, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.model.impl.project;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.ui.IWorkbenchWindow;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.navigator.DBNNode;
import org.jkiss.dbeaver.model.navigator.DBNResource;
import org.jkiss.dbeaver.model.project.DBPResourceHandler;

/**
 * Abstract resource handler
 */
public abstract class AbstractResourceHandler implements DBPResourceHandler {

    public int getFeatures(IResource resource)
    {
        return 0;
    }

    public void initializeProject(IProject project, IProgressMonitor monitor) throws CoreException, DBException
    {
        // Just do nothing
    }

    public DBNResource makeNavigatorNode(DBNNode parentNode, IResource resource) throws CoreException, DBException
    {
        return new DBNResource(parentNode, resource, this);
    }

    public void openResource(IResource resource, IWorkbenchWindow window) throws CoreException, DBException
    {
        //throw new DBException("Resource open is not implemented");
    }

    public String getTypeName(IResource resource)
    {
        return "resource";
    }

    public String getResourceDescription(IResource resource)
    {
        return resource.getName();
    }

}
