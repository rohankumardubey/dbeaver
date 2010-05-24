/*
 * Copyright (c) 2010, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.ui.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.jkiss.dbeaver.ui.DBIcon;

/**
 * Simple action
 */
public abstract class SimpleAction extends Action {

    public SimpleAction(String id, String text, String toolTip, DBIcon icon)
    {
        super(text, icon.getImageDescriptor());
        setId(id);
        //setActionDefinitionId(id);
        setToolTipText(toolTip);
    }

    @Override
    public abstract void run();

}
