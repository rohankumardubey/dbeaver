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
package org.jkiss.dbeaver.ui.editors.text;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension4;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.jkiss.dbeaver.DBeaverConstants;
import org.jkiss.dbeaver.runtime.RuntimeUtils;
import org.jkiss.dbeaver.ui.editors.ProjectFileEditorInput;
import org.jkiss.dbeaver.utils.ContentUtils;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;


/**
 * FileRefDocumentProvider
 */
public class FileRefDocumentProvider extends BaseTextDocumentProvider {

    static final Log log = LogFactory.getLog(FileRefDocumentProvider.class);

    private static final int DEFAULT_BUFFER_SIZE = 10000;

    public FileRefDocumentProvider()
    {

    }

    protected IEditorInput createNewEditorInput(IFile newFile)
    {
        return new ProjectFileEditorInput(newFile);
    }

    public static IStorage getStorageFromInput(Object element)
    {
        if (element instanceof IEditorInput) {
            IFile file = ContentUtils.getFileFromEditorInput((IEditorInput) element);
            if (file != null) {
                return file;
            }
        }
        if (element instanceof IAdaptable) {
            IStorage storage = (IStorage) ((IAdaptable) element).getAdapter(IStorage.class);
            if (storage != null) {
                return storage;
            }
        }
        return null;
    }

    @Override
    protected Document createDocument(Object element) throws CoreException
    {
        IStorage object = getStorageFromInput(element);
        if (object != null) {
            Document document = createEmptyDocument();
            if (setDocumentContent(document, object)) {
                setupDocument(document);
                return document;
            }
        }

        throw new IllegalArgumentException("Project document provider supports only editor inputs which provides IStorage facility");
    }

    protected void setupDocument(IDocument document)
    {

    }

    @Override
    public boolean isReadOnly(Object element)
    {
        IStorage storage = getStorageFromInput(element);
        if (storage  != null) {
            return storage.isReadOnly();
        }
        return super.isReadOnly(element);
    }

    @Override
    public boolean isModifiable(Object element)
    {
        return !isReadOnly(element);
    }

    @Override
    public boolean isDeleted(Object element)
    {
        IStorage storage = getStorageFromInput(element);
        if (storage instanceof IResource) {
            return !((IResource)storage).exists();
        }
        return super.isDeleted(element);
    }

    @Override
    protected void doSaveDocument(IProgressMonitor monitor, Object element, IDocument document, boolean overwrite) throws CoreException
    {
        IStorage storage = getStorageFromInput(element);
        if (storage == null) {
            throw new CoreException(new Status(Status.ERROR, DBeaverConstants.PLUGIN_ID, "Could not obtain file from editor input"));
        }
        String encoding = (storage instanceof IEncodedStorage ? ((IEncodedStorage)storage).getCharset() : ContentUtils.getDefaultFileEncoding());

        Charset charset;
        try {
            charset = Charset.forName(encoding);
        } catch (Exception ex) {
            throw new CoreException(RuntimeUtils.makeExceptionStatus(ex));
        }

        CharsetEncoder encoder = charset.newEncoder();
        encoder.onMalformedInput(CodingErrorAction.REPLACE);
        encoder.onUnmappableCharacter(CodingErrorAction.REPORT);

        InputStream stream;

        try {
            byte[] bytes;
            ByteBuffer byteBuffer = encoder.encode(CharBuffer.wrap(document.get()));
            if (byteBuffer.hasArray()) {
                bytes = byteBuffer.array();
            } else {
                bytes = new byte[byteBuffer.limit()];
                byteBuffer.get(bytes);
            }
            stream = new ByteArrayInputStream(bytes, 0, byteBuffer.limit());
        } catch (CharacterCodingException ex) {
            throw new CoreException(RuntimeUtils.makeExceptionStatus(ex));
        }

        if (storage instanceof IFile) {
            IFile file = (IFile)storage;

            if (file.exists()) {

                // inform about the upcoming content change
                fireElementStateChanging(element);
                try {
                    file.setContents(stream, true, true, monitor);
                } catch (CoreException x) {
                    // inform about failure
                    fireElementStateChangeFailed(element);
                    throw x;
                } catch (RuntimeException x) {
                    // inform about failure
                    fireElementStateChangeFailed(element);
                    throw x;
                }

            } else {
                try {
                    monitor.beginTask("Save file '" + file.getName() + "'", 2000);
                    //ContainerCreator creator = new ContainerCreator(file.getWorkspace(), file.getParent().getFullPath());
                    //creator.createContainer(new SubProgressMonitor(monitor, 1000));
                    file.create(stream, false, monitor);
                }
                finally {
                    monitor.done();
                }
            }
        }
    }

    protected boolean setDocumentContent(IDocument document, IStorage storage) throws CoreException
    {
        try {
            InputStream contentStream = storage.getContents();
            try {
                String encoding = (storage instanceof IEncodedStorage ? ((IEncodedStorage)storage).getCharset() : ContentUtils.getDefaultFileEncoding());
                setDocumentContent(document, contentStream, encoding);
            } finally {
                ContentUtils.close(contentStream);
            }
        } catch (IOException e) {
            throw new CoreException(RuntimeUtils.makeExceptionStatus(e));
        }
        return true;
    }

    protected void setDocumentContent(IDocument document, InputStream contentStream, String encoding) throws IOException
    {
        Reader in = null;

        try {
            if (encoding == null) {
                encoding = ContentUtils.DEFAULT_FILE_CHARSET;
            }

            in = new BufferedReader(new InputStreamReader(contentStream, encoding), DEFAULT_BUFFER_SIZE);
            StringBuilder buffer = new StringBuilder(DEFAULT_BUFFER_SIZE);
            char[] readBuffer = new char[2048];
            int n = in.read(readBuffer);
            while (n > 0) {
                buffer.append(readBuffer, 0, n);
                n = in.read(readBuffer);
            }

            document.set(buffer.toString());

        } finally {
            if (in != null) {
                ContentUtils.close(in);
            } else {
                ContentUtils.close(contentStream);
            }
        }
    }

    protected long computeModificationStamp(IResource resource)
    {
        long modificationStamp = resource.getModificationStamp();

        IPath path = resource.getLocation();
        if (path == null) {
            return modificationStamp;
        }

        modificationStamp = path.toFile().lastModified();
        return modificationStamp;
    }

    protected void refreshFile(IFile file) throws CoreException {
        refreshFile(file, getProgressMonitor());
    }

    @Override
    protected ElementInfo createElementInfo(Object element) throws CoreException
    {
        if (element instanceof IEditorInput) {

            IEditorInput input = (IEditorInput) element;
            IStorage storage = getStorageFromInput(input);
            if (storage instanceof IFile) {
                IFile file = (IFile)storage;
                try {
                    refreshFile(file);
                } catch (CoreException x) {
                    log.warn("Could not refresh file", x);
                }

                IDocument d;
                IStatus s = null;

                try {
                    d = createDocument(element);
                } catch (CoreException x) {
                    log.warn("Could not create document", x);
                    s = x.getStatus();
                    d = createEmptyDocument();
                }

                // Set the initial line delimiter
                if (d instanceof IDocumentExtension4) {
                    String initialLineDelimiter = ContentUtils.getDefaultLineSeparator();
                    if (initialLineDelimiter != null) {
                        ((IDocumentExtension4) d).setInitialLineDelimiter(initialLineDelimiter);
                    }
                }

                IAnnotationModel m = createAnnotationModel(element);
                FileSynchronizer f = new FileSynchronizer(input);
                f.install();

                FileInfo info = new FileInfo(d, m, f);
                info.modificationStamp = computeModificationStamp(file);
                info.fStatus = s;

                return info;
            }
        }

        return super.createElementInfo(element);
    }

    @Override
    protected void disposeElementInfo(Object element, ElementInfo info)
    {
        if (info instanceof FileInfo) {
            FileInfo fileInfo = (FileInfo) info;
            if (fileInfo.fileSynchronizer != null) {
                fileInfo.fileSynchronizer.uninstall();
            }
        }

        super.disposeElementInfo(element, info);
    }

    protected void refreshFile(IFile file, IProgressMonitor monitor) throws CoreException {
        if (file != null) {
            try {
                file.refreshLocal(IResource.DEPTH_INFINITE, monitor);
            } catch (OperationCanceledException x) {
                // do nothing
            }
        }
    }

    /**
     * Updates the element info to a change of the file content and sends out
     * appropriate notifications.
     *
     * @param fileEditorInput the input of an text editor
     */
    protected void handleElementContentChanged(IEditorInput fileEditorInput)
    {
        FileInfo info = (FileInfo) getElementInfo(fileEditorInput);
        if (info == null) {
            return;
        }

        IStorage storage = getStorageFromInput(fileEditorInput);
        if (storage instanceof IFile) {
            IFile file = (IFile)storage;
            IDocument document = createEmptyDocument();
            IStatus status = null;

            try {

                try {
                    refreshFile(file);
                } catch (CoreException x) {
                    log.error("handleElementContentChanged", x);
                }

                setDocumentContent(document, file);
            } catch (CoreException x) {
                status = x.getStatus();
            }

            String newContent = document.get();

            if (!newContent.equals(info.fDocument.get())) {

                // set the new content and fire content related events
                fireElementContentAboutToBeReplaced(fileEditorInput);

                removeUnchangedElementListeners(fileEditorInput, info);

                info.fDocument.removeDocumentListener(info);
                info.fDocument.set(newContent);
                info.fCanBeSaved = false;
                info.modificationStamp = computeModificationStamp(file);
                info.fStatus = status;

                addUnchangedElementListeners(fileEditorInput, info);

                fireElementContentReplaced(fileEditorInput);

            } else {

                removeUnchangedElementListeners(fileEditorInput, info);

                // fires only the dirty state related event
                info.fCanBeSaved = false;
                info.modificationStamp = computeModificationStamp(file);
                info.fStatus = status;

                addUnchangedElementListeners(fileEditorInput, info);

                fireElementDirtyStateChanged(fileEditorInput, false);
            }
        }
    }

    /**
     * Sends out the notification that the file serving as document input has been moved.
     *
     * @param fileEditorInput the input of an text editor
     * @param path            the path of the new location of the file
     */
    protected void handleElementMoved(IEditorInput fileEditorInput, IPath path)
    {
        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        IFile newFile = workspace.getRoot().getFile(path);
        fireElementMoved(fileEditorInput, createNewEditorInput(newFile));
    }

    /**
     * Sends out the notification that the file serving as document input has been deleted.
     *
     * @param fileEditorInput the input of an text editor
     */
    protected void handleElementDeleted(IEditorInput fileEditorInput)
    {
        fireElementDeleted(fileEditorInput);
    }

    protected abstract class SafeChange implements Runnable {

        private IEditorInput editorInput;

        public SafeChange(IEditorInput input)
        {
            editorInput = input;
        }

        protected abstract void execute(IEditorInput input) throws Exception;

        @Override
        public void run()
        {

            if (getElementInfo(editorInput) == null) {
                fireElementStateChangeFailed(editorInput);
                return;
            }
            try {
                execute(editorInput);
            } catch (Exception e) {
                fireElementStateChangeFailed(editorInput);
            }
        }
    }

    /**
     * Synchronizes the document with external resource changes.
     */
    protected class FileSynchronizer implements IResourceChangeListener, IResourceDeltaVisitor {

        protected IEditorInput fileEditorInput;
        protected boolean isInstalled = false;

        /**
         * Creates a new file synchronizer. Is not yet installed on a resource.
         *
         * @param fileEditorInput the editor input to be synchronized
         */
        public FileSynchronizer(IEditorInput fileEditorInput)
        {
            this.fileEditorInput = fileEditorInput;
        }

        /**
         * Returns the file wrapped by the file editor input.
         *
         * @return the file wrapped by the editor input associated with that synchronizer
         */
        protected IFile getFile()
        {
            IStorage storage = getStorageFromInput(fileEditorInput);
            return storage instanceof IFile ? (IFile)storage : null;
        }

        /**
         * Installs the synchronizer on the input's file.
         */
        public void install()
        {
            getFile().getWorkspace().addResourceChangeListener(this);
            isInstalled = true;
        }

        /**
         * Uninstalls the synchronizer from the input's file.
         */
        public void uninstall()
        {
            getFile().getWorkspace().removeResourceChangeListener(this);
            isInstalled = false;
        }

        @Override
        public void resourceChanged(IResourceChangeEvent e)
        {
            IResourceDelta delta = e.getDelta();
            try {
                if (delta != null && isInstalled) {
                    delta.accept(this);
                }
            } catch (CoreException x) {
                log.warn("Error handling resourceChanged", x);
            }
        }

        @Override
        public boolean visit(IResourceDelta delta) throws CoreException
        {
            if (delta == null) {
                return false;
            }

            IFile file = getFile();
            if (file == null) {
                return false;
            }

            delta = delta.findMember(file.getFullPath());

            if (delta == null) {
                return false;
            }

            Runnable runnable = null;

            switch (delta.getKind()) {
                case IResourceDelta.CHANGED:
                    FileInfo info = (FileInfo) getElementInfo(fileEditorInput);
                    if (info == null || info.fCanBeSaved) {
                        break;
                    }

                    boolean isSynchronized = computeModificationStamp(file) == info.modificationStamp;
                    if ((IResourceDelta.ENCODING & delta.getFlags()) != 0 && isSynchronized) {
                        runnable = new SafeChange(fileEditorInput) {
                            @Override
                            protected void execute(IEditorInput input) throws Exception
                            {
                                handleElementContentChanged(input);
                            }
                        };
                    }

                    if (runnable == null && (IResourceDelta.CONTENT & delta.getFlags()) != 0 && !isSynchronized) {
                        runnable = new SafeChange(fileEditorInput) {
                            @Override
                            protected void execute(IEditorInput input) throws Exception
                            {
                                handleElementContentChanged(input);
                            }
                        };
                    }
                    break;

                case IResourceDelta.REMOVED:
                    if ((IResourceDelta.MOVED_TO & delta.getFlags()) != 0) {
                        final IPath path = delta.getMovedToPath();
                        runnable = new SafeChange(fileEditorInput) {
                            @Override
                            protected void execute(IEditorInput input) throws Exception
                            {
                                handleElementMoved(input, path);
                            }
                        };
                    } else {
                        info = (FileInfo) getElementInfo(fileEditorInput);
                        if (info != null && !info.fCanBeSaved) {
                            runnable = new SafeChange(fileEditorInput) {
                                @Override
                                protected void execute(IEditorInput input) throws Exception
                                {
                                    handleElementDeleted(input);
                                }
                            };
                        }
                    }
                    break;
            }

            if (runnable != null) {
                update(runnable);
            }

            return false;
        }

        /**
         * Posts the update code "behind" the running operation.
         *
         * @param runnable the update code
         */
        protected void update(Runnable runnable)
        {

            if (runnable instanceof SafeChange) {
                fireElementStateChanging(fileEditorInput);
            }

            IWorkbench workbench = PlatformUI.getWorkbench();
            IWorkbenchWindow[] windows = workbench.getWorkbenchWindows();
            if (windows != null && windows.length > 0) {
                Display display = windows[0].getShell().getDisplay();
                display.asyncExec(runnable);
            } else {
                runnable.run();
            }
        }
    }


    /**
     * Bundle of all required information to allow files as underlying document resources.
     */
    protected class FileInfo extends ElementInfo {

        /**
         * The file synchronizer.
         */
        public FileSynchronizer fileSynchronizer;
        /**
         * The time stamp at which this provider changed the file.
         */
        public long modificationStamp = IResource.NULL_STAMP;

        public FileInfo(IDocument document, IAnnotationModel model, FileSynchronizer fileSynchronizer)
        {
            super(document, model);
            this.fileSynchronizer = fileSynchronizer;
        }
    }

}
