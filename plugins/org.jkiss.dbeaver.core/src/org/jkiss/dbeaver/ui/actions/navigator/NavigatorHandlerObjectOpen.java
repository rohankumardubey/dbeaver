/*
 * Copyright (c) 2011, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.ui.actions.navigator;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;
import org.jkiss.dbeaver.model.edit.DBOEditorInline;
import org.jkiss.dbeaver.model.navigator.DBNDatabaseFolder;
import org.jkiss.dbeaver.model.navigator.DBNDatabaseNode;
import org.jkiss.dbeaver.model.navigator.DBNDatabaseObject;
import org.jkiss.dbeaver.model.navigator.DBNResource;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.editors.entity.EntityEditor;
import org.jkiss.dbeaver.ui.editors.entity.EntityEditorInput;
import org.jkiss.dbeaver.ui.editors.folder.FolderEditor;
import org.jkiss.dbeaver.ui.editors.folder.FolderEditorInput;
import org.jkiss.dbeaver.ui.editors.object.ObjectEditorInput;

import java.util.Iterator;

public class NavigatorHandlerObjectOpen extends NavigatorHandlerObjectBase {

    public Object execute(ExecutionEvent event) throws ExecutionException {
        final ISelection selection = HandlerUtil.getCurrentSelection(event);

        if (selection instanceof IStructuredSelection) {
            final IStructuredSelection structSelection = (IStructuredSelection)selection;
            for (Iterator iter = structSelection.iterator(); iter.hasNext(); ) {
                Object element = iter.next();
                DBNDatabaseNode node = null;
                if (element instanceof DBNResource) {
                    openResource((DBNResource)element, HandlerUtil.getActiveWorkbenchWindow(event));
                    continue;
                } else if (element instanceof DBNDatabaseNode) {
                    node = (DBNDatabaseNode)element;
                } else {
                    DBSObject object = (DBSObject) Platform.getAdapterManager().getAdapter(element, DBSObject.class);
                    if (object != null) {
                        node = getNodeByObject(object);
                    }
                }
                if (node != null) {
                    openEntityEditor(node, null, HandlerUtil.getActiveWorkbenchWindow(event));
                }
            }
        }
        return null;
    }

    private void openResource(DBNResource resourceNode, IWorkbenchWindow window)
    {
        try {
            resourceNode.openResource(window);
        } catch (Exception e) {
            UIUtils.showErrorDialog(window.getShell(), "Open resource", "Can't open resource", e);
        }
    }

    public static void openEntityEditor(DBNDatabaseNode selectedNode, String defaultPageId, IWorkbenchWindow workbenchWindow)
    {
        if (selectedNode.getObject() instanceof DBOEditorInline) {
            ((DBOEditorInline)selectedNode.getObject()).editObject(workbenchWindow);
            return;
        }
        IWorkbenchPart oldActivePart = workbenchWindow.getActivePage().getActivePart();
        try {
            for (IEditorReference ref : workbenchWindow.getActivePage().getEditorReferences()) {
                if (ref.getEditorInput() instanceof EntityEditorInput && ((EntityEditorInput)ref.getEditorInput()).getTreeNode() == selectedNode) {
                    workbenchWindow.getActivePage().activate(ref.getEditor(false));
                    return;
                }
            }
            if (selectedNode instanceof DBNDatabaseFolder) {
                FolderEditorInput folderInput = new FolderEditorInput((DBNDatabaseFolder)selectedNode);
                folderInput.setDefaultPageId(defaultPageId);
                workbenchWindow.getActivePage().openEditor(
                    folderInput,
                    FolderEditor.class.getName());
            } else if (selectedNode instanceof DBNDatabaseObject) {
                DBNDatabaseObject objectNode = (DBNDatabaseObject) selectedNode;
                ObjectEditorInput objectInput = new ObjectEditorInput(objectNode);
                workbenchWindow.getActivePage().openEditor(
                    objectInput,
                    objectNode.getMeta().getEditorId());

            } else if (selectedNode.getObject() != null) {
                EntityEditorInput editorInput = new EntityEditorInput(selectedNode);
                editorInput.setDefaultPageId(defaultPageId);
                workbenchWindow.getActivePage().openEditor(
                    editorInput,
                    EntityEditor.class.getName());
            }
        } catch (Exception ex) {
            UIUtils.showErrorDialog(workbenchWindow.getShell(), "Open entity", "Can't open entity", ex);
        }
        finally {
            // Reactivate navigator
            // Actually it still focused but we need to use it's selection
            // I think it is an eclipse bug
            if (!(oldActivePart instanceof IEditorPart)) {
                workbenchWindow.getActivePage().activate(oldActivePart);
            }
        }
    }

}