/*
 * Copyright (c) 2011, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.model.project;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.ui.IWorkbenchWindow;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.navigator.DBNNode;

/**
 * Resource handler
 */
public interface DBPResourceHandler {

    public static final QualifiedName PROP_PROJECT_ID = new QualifiedName("org.jkiss.dbeaver", "project-id");
    public static final QualifiedName PROP_PROJECT_ACTIVE = new QualifiedName("org.jkiss.dbeaver", "project-active");
    public static final QualifiedName PROP_RESOURCE_TYPE = new QualifiedName("org.jkiss.dbeaver", "resource-type");

    void initializeProject(IProject project, IProgressMonitor monitor) throws CoreException, DBException;

    DBNNode makeNavigatorNode(DBNNode parentNode, IResource resource) throws CoreException, DBException;

    void openResource(IResource resource, IWorkbenchWindow window) throws CoreException, DBException;

}
