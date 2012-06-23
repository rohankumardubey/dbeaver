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
package org.jkiss.dbeaver.core;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.application.ActionBarAdvisor;
import org.eclipse.ui.application.IActionBarConfigurer;
import org.eclipse.ui.application.IWorkbenchWindowConfigurer;
import org.eclipse.ui.application.WorkbenchWindowAdvisor;
import org.eclipse.ui.part.EditorInputTransfer;
import org.jkiss.dbeaver.model.project.DBPProjectListener;
import org.jkiss.dbeaver.registry.ProjectRegistry;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.dialogs.ConfirmationDialog;
import org.jkiss.dbeaver.ui.dialogs.connection.ConnectionDialog;
import org.jkiss.dbeaver.ui.dialogs.connection.NewConnectionWizard;
import org.jkiss.dbeaver.ui.editors.content.ContentEditorInput;
import org.jkiss.dbeaver.ui.preferences.PrefConstants;

public class ApplicationWorkbenchWindowAdvisor extends WorkbenchWindowAdvisor implements DBPProjectListener {
    //static final Log log = LogFactory.getLog(ApplicationWorkbenchWindowAdvisor.class);

    public ApplicationWorkbenchWindowAdvisor(IWorkbenchWindowConfigurer configurer)
    {
        super(configurer);

        DBeaverCore.getInstance().getProjectRegistry().addProjectListener(this);
    }

    @Override
    public ActionBarAdvisor createActionBarAdvisor(IActionBarConfigurer configurer)
    {
        return new ApplicationActionBarAdvisor(configurer);
    }

    @Override
    public void preWindowOpen()
    {
        IWorkbenchWindowConfigurer configurer = getWindowConfigurer();
        configurer.setInitialSize(new Point(600, 400));
        configurer.setShowCoolBar(true);
        configurer.setShowStatusLine(true);
        configurer.setShowProgressIndicator(true);
        configurer.configureEditorAreaDropListener(new EditorAreaDropAdapter());
        configurer.addEditorAreaTransfer(EditorInputTransfer.getInstance());

        //PreferenceManager preferenceManager = PlatformUI.getWorkbench().getPreferenceManager();
        //preferenceManager.remove("org.eclipse.ui.preferencePages.Workbench/org.eclipse.ui.preferencePages.Perspectives");
        //preferenceManager.remove("org.eclipse.ui.preferencePages.Workbench/org.eclipse.ui.preferencePages.Workspace");

        // Show heap usage
        //PlatformUI.getPreferenceStore().setValue(IWorkbenchPreferenceConstants.SHOW_MEMORY_MONITOR, true);
    }

    /*
    org.eclipse.ui.preferencePages.Editors
    org.eclipse.ui.preferencePages.Views
    org.eclipse.ui.preferencePages.Keys
    org.eclipse.ui.preferencePages.ContentTypes
    */
    @Override
    public void postWindowCreate()
    {
        Shell activeShell = UIUtils.getActiveShell();
        if (activeShell != null) {
            activeShell.setMaximized(true);
        }
        updateWindowTitle();
    }

    @Override
    public boolean preWindowShellClose()
    {
        IWorkbenchWindow window = getWindowConfigurer().getWindow();

        try {
            if (!ConfirmationDialog.confirmAction(window.getShell(), PrefConstants.CONFIRM_EXIT)) {
                return false;
            }
            // Close al content editors
            // They are locks resources which are shared between other editors
            // So we need to close em first
            IWorkbenchPage workbenchPage = window.getActivePage();
            IEditorReference[] editors = workbenchPage.getEditorReferences();
            for (IEditorReference editor : editors) {
                IEditorPart editorPart = editor.getEditor(false);
                if (editorPart != null && editorPart.getEditorInput() instanceof ContentEditorInput) {
                    workbenchPage.closeEditor(editorPart, false);
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        // Do its job
        return super.preWindowShellClose();
    }

    @Override
    public void postWindowOpen() {
        super.postWindowOpen();

        final ProjectRegistry projectRegistry = DBeaverCore.getInstance().getProjectRegistry();
        if (projectRegistry.getActiveDataSourceRegistry().getDataSources().isEmpty()) {
            // Open New Connection wizard
            Display.getCurrent().asyncExec(new Runnable() {
                @Override
                public void run() {
                    IWorkbenchWindow window = getWindowConfigurer().getWindow();
                    final ProjectRegistry projectRegistry = DBeaverCore.getInstance().getProjectRegistry();
                    ConnectionDialog dialog = new ConnectionDialog(window
                        , new NewConnectionWizard(projectRegistry.getActiveDataSourceRegistry()));
                    dialog.open();
                }
            });
        }
    }

    @Override
    public void postWindowClose()
    {
        ProjectRegistry projectRegistry = DBeaverCore.getInstance().getProjectRegistry();
        if (projectRegistry != null) {
            projectRegistry.removeProjectListener(this);
        }
        super.postWindowClose();
    }

    private void updateWindowTitle()
    {
        IProject activeProject = DBeaverCore.getInstance().getProjectRegistry().getActiveProject();
        String title = Platform.getProduct().getName();
        if (activeProject != null) {
            title += " - " + activeProject.getName(); //$NON-NLS-1$
        }
        getWindowConfigurer().getWindow().getShell().setText(title);
    }

    @Override
    public void handleActiveProjectChange(IProject oldValue, IProject newValue)
    {
        updateWindowTitle();
    }

    public class EditorAreaDropAdapter extends DropTargetAdapter
    {
    }

}

