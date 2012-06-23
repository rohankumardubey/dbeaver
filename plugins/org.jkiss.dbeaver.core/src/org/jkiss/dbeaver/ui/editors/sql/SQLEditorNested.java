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
package org.jkiss.dbeaver.ui.editors.sql;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.PartInitException;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.core.DBeaverCore;
import org.jkiss.dbeaver.ext.IDatabaseEditorInput;
import org.jkiss.dbeaver.ext.ui.IActiveWorkbenchPart;
import org.jkiss.dbeaver.ext.ui.IRefreshablePart;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.exec.compile.DBCCompileLog;
import org.jkiss.dbeaver.model.exec.compile.DBCSourceHost;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.runtime.DBRRunnableWithProgress;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.runtime.RuntimeUtils;
import org.jkiss.dbeaver.ui.ActionUtils;
import org.jkiss.dbeaver.ui.DBIcon;
import org.jkiss.dbeaver.ui.ICommandIds;
import org.jkiss.dbeaver.ui.controls.ObjectCompilerLogViewer;
import org.jkiss.dbeaver.ui.controls.ProgressPageControl;
import org.jkiss.dbeaver.ui.editors.text.BaseTextDocumentProvider;

import java.lang.reflect.InvocationTargetException;

/**
 * SQLEditorNested
 */
public abstract class SQLEditorNested<T extends DBSObject>
    extends SQLEditorBase
    implements IActiveWorkbenchPart, IRefreshablePart, DBCSourceHost
{

    private EditorPageControl pageControl;
    private IEditorInput lazyInput;
    private ObjectCompilerLogViewer compileLog;
    private Control editorControl;
    private SashForm editorSash;

    public SQLEditorNested() {
        super();

        setDocumentProvider(new ObjectDocumentProvider());
        //setHasVerticalRuler(false);
    }

    @Override
    public IDatabaseEditorInput getEditorInput() {
        return (IDatabaseEditorInput)super.getEditorInput();
    }

    @Override
    public T getSourceObject()
    {
        IDatabaseEditorInput editorInput = getEditorInput();
        if (editorInput == null) {
            return null;
        }
        return (T) editorInput.getDatabaseObject();
    }

    @Override
    public DBPDataSource getDataSource() {
        IDatabaseEditorInput editorInput = getEditorInput();
        if (editorInput == null) {
            return null;
        }
        return editorInput.getDataSource();
    }

    @Override
    public void createPartControl(Composite parent)
    {
        pageControl = new EditorPageControl(parent, SWT.SHEET);

//        ProgressPageControl progressControl = null;
//        if (getSite() instanceof MultiPageEditorSite) {
//            MultiPageEditorPart ownerEditor = ((MultiPageEditorSite) getSite()).getMultiPageEditor();
//            if (ownerEditor instanceof IProgressControlProvider) {
//                progressControl = ((IProgressControlProvider)ownerEditor).getProgressControl();
//            }
//        }

        editorSash = new SashForm(pageControl.createContentContainer(), SWT.VERTICAL | SWT.SMOOTH);
        super.createPartControl(editorSash);

        editorControl = editorSash.getChildren()[0];
        compileLog = new ObjectCompilerLogViewer(editorSash);

//        if (progressControl != null) {
//            pageControl.substituteProgressPanel(progressControl);
//        } else {
//            pageControl.createProgressPanel();
//        }
        pageControl.createProgressPanel();

        editorSash.setWeights(new int[] {70, 30});
        editorSash.setMaximizedControl(editorControl);
    }

    @Override
    public void init(IEditorSite site, IEditorInput input) throws PartInitException {
        lazyInput = input;
        setSite(site);
    }

    @Override
    public void doSave(IProgressMonitor progressMonitor) {
        if (lazyInput != null) {
            return;
        }
        super.doSave(progressMonitor);
    }

    @Override
    public void activatePart() {
        if (lazyInput != null) {
            try {
                super.init(getEditorSite(), lazyInput);
                reloadSyntaxRules();

                //pageControl.setInfo(OracleMessages.editors_oracle_source_abstract_editor_state + getSourceObject().getObjectState().getTitle());
                lazyInput = null;
            } catch (PartInitException e) {
                log.error(e);
            }
        }
    }

    @Override
    public void deactivatePart() {
    }

    @Override
    public void refreshPart(Object source, boolean force) {
        if (lazyInput == null && force) {
            try {
                super.init(getEditorSite(), getEditorInput());
                reloadSyntaxRules();
                setFocus();
            } catch (PartInitException e) {
                log.error(e);
            }
        }
    }

    protected boolean isReadOnly()
    {
        return false;
    }

    protected String getCompileCommandId()
    {
        return null;
    }

    private class ObjectDocumentProvider extends BaseTextDocumentProvider {
        @Override
        public boolean isReadOnly(Object element) {
            return SQLEditorNested.this.isReadOnly();
        }

        @Override
        public boolean isModifiable(Object element) {
            return !SQLEditorNested.this.isReadOnly();
        }

        @Override
        protected IDocument createDocument(Object element) throws CoreException {
            final Document document = new Document();

            try {
                DBeaverCore.getInstance().runInProgressService(new DBRRunnableWithProgress() {
                    @Override
                    public void run(DBRProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
                        try {
                            String sourceText = getSourceText(monitor);
                            if (sourceText != null) {
                                document.set(sourceText);
                            }
                        } catch (DBException e) {
                            throw new InvocationTargetException(e);
                        }
                    }
                });
            } catch (InvocationTargetException e) {
                throw new CoreException(RuntimeUtils.makeExceptionStatus(e.getTargetException()));
            } catch (InterruptedException e) {
                // just skip it
            }

            return document;
        }

        @Override
        protected void doSaveDocument(IProgressMonitor monitor, Object element, IDocument document, boolean overwrite) throws CoreException {
            setSourceText(document.get());
        }
    }

    @Override
    public DBCCompileLog getCompileLog()
    {
        return compileLog;
    }

    @Override
    public void setCompileInfo(String message, boolean error)
    {
        pageControl.setInfo(message);
    }

    @Override
    public void positionSource(int line, int position)
    {
        try {
            final IRegion lineInfo = getTextViewer().getDocument().getLineInformation(line - 1);
            final int offset = lineInfo.getOffset() + position - 1;
            super.selectAndReveal(offset, 0);
            //textEditor.setFocus();
        } catch (BadLocationException e) {
            log.warn(e);
            // do nothing
        }
    }

    @Override
    public void showCompileLog()
    {
        editorSash.setMaximizedControl(null);
        compileLog.layoutLog();
    }

    protected abstract String getSourceText(DBRProgressMonitor monitor)
        throws DBException;

    protected abstract void setSourceText(String sourceText);

    private class EditorPageControl extends ProgressPageControl {

        private ToolBarManager toolBarManager;

        public EditorPageControl(Composite parent, int style)
        {
            super(parent, style);
        }

        @Override
        public void dispose()
        {
            toolBarManager.dispose();
            super.dispose();
        }

        @Override
        public Composite createProgressPanel(Composite container)
        {
            Composite infoGroup = super.createProgressPanel(container);

            toolBarManager = new ToolBarManager(SWT.HORIZONTAL | SWT.FLAT);

            toolBarManager.add(ActionUtils.makeCommandContribution(DBeaverCore.getActiveWorkbenchWindow(), ICommandIds.CMD_OPEN_FILE));
            toolBarManager.add(ActionUtils.makeCommandContribution(DBeaverCore.getActiveWorkbenchWindow(), ICommandIds.CMD_SAVE_FILE));
            String compileCommandId = getCompileCommandId();
            if (compileCommandId != null) {
                toolBarManager.add(new Separator());
                toolBarManager.add(ActionUtils.makeCommandContribution(DBeaverCore.getActiveWorkbenchWindow(), compileCommandId));
                toolBarManager.add(new ViewLogAction());
            }

            toolBarManager.createControl(infoGroup);

            return infoGroup;
        }
    }

    public class ViewLogAction extends Action
    {
        public ViewLogAction()
        {
            super("View compile log", DBIcon.COMPILE_LOG.getImageDescriptor()); //$NON-NLS-2$
        }

        @Override
        public void run()
        {
            if (getTextViewer().getControl().isDisposed()) {
                return;
            }
            if (editorSash.getMaximizedControl() == null) {
                editorSash.setMaximizedControl(editorControl);
            } else {
                showCompileLog();
            }
        }

    }

}
