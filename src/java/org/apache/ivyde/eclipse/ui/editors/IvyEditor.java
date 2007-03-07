package org.apache.ivyde.eclipse.ui.editors;

import org.apache.ivyde.eclipse.cpcontainer.IvyClasspathContainer;
import org.apache.ivyde.eclipse.ui.core.IvyFileEditorInput;
import org.apache.ivyde.eclipse.ui.editors.pages.OverviewFormPage;
import org.apache.ivyde.eclipse.ui.editors.xml.XMLEditor;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.forms.editor.FormEditor;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.FileEditorInput;


public class IvyEditor extends FormEditor implements IResourceChangeListener {
    public final static String ID = "org.apache.ivyde.editors.IvyEditor";
    private XMLEditor xmlEditor;
    private Browser _browser;

    /**
     * Creates a multi-page editor example.
     */
    public IvyEditor() {
        super();
        ResourcesPlugin.getWorkspace().addResourceChangeListener(this);
    }
    
    protected void setInput(IEditorInput input) {
        IvyFileEditorInput ivyFileEditorInput = null;
        if (input instanceof FileEditorInput) {
            FileEditorInput fei = (FileEditorInput) input;
            IFile file = ((FileEditorInput)input).getFile();
            ivyFileEditorInput = new IvyFileEditorInput(file);
        } else if (input instanceof IvyFileEditorInput) {
            ivyFileEditorInput = (IvyFileEditorInput) input;
        }
        super.setInput(ivyFileEditorInput);
        if (ivyFileEditorInput.getFile() != null) {
            if (xmlEditor != null) {
                xmlEditor.setFile(ivyFileEditorInput.getFile());
            }
        }
        //deprectated but we need retro compatibility
        setTitle(ivyFileEditorInput.getFile().getName());
    }
    
    void createPageXML() {
        try {
            xmlEditor = new XMLEditor();
            xmlEditor.setFile(((IvyFileEditorInput)getEditorInput()).getFile());
            int index = addPage(xmlEditor, getEditorInput());
            setPageText(index, xmlEditor.getTitle());
        } catch (PartInitException e) {
            ErrorDialog.openError(getSite().getShell(), "Error creating nested text editor", null, e.getStatus());
        }
    }

    void createPageOverView() {
        try {
            int index = addPage(new OverviewFormPage(this));
            setPageText(index, "Information");
        } catch (PartInitException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
    }

    void createPagePreview() {
        _browser = new Browser(getContainer(), SWT.NONE);
        _browser.setUrl(((IvyFileEditorInput)getEditorInput()).getPath().toOSString());
        int index = addPage(_browser);
        setPageText(index, "Preview");
    }

    /**
     * Creates the pages of the multi-page editor.
     */
    protected void addPages() {
//        createPageOverView();
        createPageXML();
//        createPagePreview();
    }

    /**
     * The <code>MultiPageEditorPart</code> implementation of this <code>IWorkbenchPart</code> method disposes all nested editors. Subclasses may extend.
     */
    public void dispose() {
        ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
        super.dispose();
    }

    /**
     * Saves the multi-page editor's document.
     */
    public void doSave(IProgressMonitor monitor) {
        xmlEditor.doSave(monitor);       
        IFile file = ((IvyFileEditorInput)getEditorInput()).getFile();
        IvyClasspathContainer.resolveIfNeeded(file);
    }

    /**
     * Saves the multi-page editor's document as another file. Also updates the text for page 0's tab, and updates this multi-page editor's input to correspond to the nested editor's.
     */
    public void doSaveAs() {
        xmlEditor.doSaveAs();
        setPageText(0, xmlEditor.getTitle());
        setInput(xmlEditor.getEditorInput());
    }

    /*
     * (non-Javadoc) Method declared on IEditorPart
     */
    public void gotoMarker(IMarker marker) {
        setActivePage(0);
        IDE.gotoMarker(getEditor(0), marker);
    }

    /**
     * The <code>MultiPageEditorExample</code> implementation of this method checks that the input is an instance of <code>IFileEditorInput</code>.
     */
    public void init(IEditorSite site, IEditorInput editorInput) throws PartInitException {
        if (!(editorInput instanceof IFileEditorInput))
            throw new PartInitException("Invalid Input: Must be IFileEditorInput");
        super.init(site, editorInput);
    }

    /*
     * (non-Javadoc) Method declared on IEditorPart.
     */
    public boolean isSaveAsAllowed() {
        return xmlEditor.isSaveAsAllowed();
    }

    /**
     * Calculates the contents of page 2 when the it is activated.
     */
    protected void pageChange(int newPageIndex) {
        super.pageChange(newPageIndex);
        if (newPageIndex == 1) {
            _browser.refresh();
        }
    }

    /**
     * Closes all project files on project close.
     */
    public void resourceChanged(IResourceChangeEvent event) {
        if (event.getType() == IResourceChangeEvent.PRE_CLOSE) {
            final IResource res = event.getResource();
            Display.getDefault().asyncExec(new Runnable() {
                public void run() {
                    IWorkbenchPage[] pages = getSite().getWorkbenchWindow().getPages();
                    for (int i = 0; i < pages.length; i++) {
                        if (((FileEditorInput) xmlEditor.getEditorInput()).getFile().getProject().equals(res)) {
                            IEditorPart editorPart = pages[i].findEditor(xmlEditor.getEditorInput());
                            pages[i].closeEditor(editorPart, true);
                        }
                    }
                }
            });
        }
    }
}