package ru.xelgo.edt.formupdate.ui;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.statushandlers.StatusManager;

import com._1c.g5.v8.dt.core.platform.IExtensionProject;
import com._1c.g5.v8.dt.core.platform.IV8Project;
import com._1c.g5.v8.dt.core.platform.IV8ProjectManager;
import com._1c.g5.v8.dt.form.model.Form;
import com._1c.g5.v8.dt.md.extension.adopt.IModelObjectAdopter;
import com._1c.g5.v8.dt.metadata.mdclass.AbstractForm;
import com._1c.g5.v8.dt.metadata.mdclass.BasicForm;
import com._1c.g5.v8.dt.metadata.mdclass.Configuration;
import com._1c.g5.wiring.ServiceAccess;

/**
 * Finds and batch-updates extension forms from the source configuration.
 */
public class UpdateFormHandler
    extends AbstractHandler
{
    private static final String PLUGIN_ID = "ru.xelgo.edt.formupdate.ui"; //$NON-NLS-1$
    private static final boolean DEBUG_LOGGING = false;

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException
    {
        logInfo("Command started"); //$NON-NLS-1$
        IModelObjectAdopter modelObjectAdopter = ServiceAccess.get(IModelObjectAdopter.class);
        IV8ProjectManager v8ProjectManager = ServiceAccess.get(IV8ProjectManager.class);
        if (modelObjectAdopter == null || v8ProjectManager == null)
        {
            logInfo("Required EDT services are not available. modelObjectAdopter={0}, v8ProjectManager={1}", //$NON-NLS-1$
                modelObjectAdopter, v8ProjectManager);
            throw new ExecutionException("Required EDT services are not available"); //$NON-NLS-1$
        }

        IExtensionProject extensionProject = getSelectedExtensionProject(event, v8ProjectManager);
        if (extensionProject == null)
        {
            logInfo("Extension project was not detected from current selection"); //$NON-NLS-1$
            showInfo(event, Messages.UpdateFormHandler_NoExtensionProject);
            return null;
        }
        logInfo("Extension project detected: {0}", getProjectName(extensionProject)); //$NON-NLS-1$

        List<FormUpdateCandidate> candidates = findCandidates(event, modelObjectAdopter, extensionProject);
        logInfo("Search finished. Candidate count: {0}", candidates.size()); //$NON-NLS-1$
        if (candidates.isEmpty())
        {
            showInfo(event, Messages.UpdateFormHandler_NoCandidates);
            return null;
        }

        Shell shell = HandlerUtil.getActiveShell(event);
        BatchUpdateFormsDialog dialog = new BatchUpdateFormsDialog(shell, candidates, new BatchUpdateFormsDialog.UpdateOperation()
        {
            @Override
            public boolean prepare()
            {
                return prepareBatchUpdate(event);
            }

            @Override
            public void update(FormUpdateCandidate candidate, IProgressMonitor monitor) throws CoreException
            {
                logInfo("Updating candidate started. Owner: {0}, form: {1}", candidate.getOwnerName(), //$NON-NLS-1$
                    candidate.getFormName());
                modelObjectAdopter.updateAdopted(candidate.getSourceForm(), extensionProject, monitor);
                logInfo("Updating candidate finished. Owner: {0}, form: {1}", candidate.getOwnerName(), //$NON-NLS-1$
                    candidate.getFormName());
            }
        });
        dialog.open();
        return null;
    }

    private List<FormUpdateCandidate> findCandidates(ExecutionEvent event, IModelObjectAdopter modelObjectAdopter,
        IExtensionProject extensionProject)
        throws ExecutionException
    {
        List<FormUpdateCandidate> candidates = new ArrayList<>();
        Shell shell = HandlerUtil.getActiveShell(event);
        IRunnableWithProgress runnable = monitor -> {
            monitor.beginTask(valueOrDefault(Messages.UpdateFormHandler_Searching, "Searching extension forms"), //$NON-NLS-1$
                IProgressMonitor.UNKNOWN);
            logInfo("Scanning forms for project: {0}", getProjectName(extensionProject)); //$NON-NLS-1$
            scanForms(extensionProject, modelObjectAdopter, candidates);
        };

        try
        {
            new ProgressMonitorDialog(shell).run(true, false, runnable);
        }
        catch (InvocationTargetException e)
        {
            throw new ExecutionException("Failed to search extension forms", e.getCause()); //$NON-NLS-1$
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new ExecutionException("Extension form search was interrupted", e); //$NON-NLS-1$
        }
        return candidates;
    }

    private void scanForms(IExtensionProject extensionProject, IModelObjectAdopter modelObjectAdopter,
        List<FormUpdateCandidate> candidates)
    {
        Configuration configuration = extensionProject.getConfiguration();
        if (configuration == null)
        {
            logInfo("Extension project configuration is null: {0}", getProjectName(extensionProject)); //$NON-NLS-1$
            return;
        }

        logInfo("Extension configuration found. Root class: {0}", configuration.eClass().getName()); //$NON-NLS-1$
        int[] counters = new int[2];
        collectForm(configuration, extensionProject, modelObjectAdopter, candidates);
        for (Iterator<EObject> iterator = configuration.eAllContents(); iterator.hasNext();)
        {
            EObject object = iterator.next();
            if (object instanceof BasicForm)
                counters[0]++;
            if (object instanceof Form)
                counters[1]++;
            collectForm(object, extensionProject, modelObjectAdopter, candidates);
        }
        logInfo("Scan counters. BasicForm objects: {0}, Form objects: {1}, candidates: {2}", counters[0], counters[1], //$NON-NLS-1$
            candidates.size());
        if (candidates.isEmpty())
            scanFormResources(extensionProject, modelObjectAdopter, candidates);
    }

    private void scanFormResources(IExtensionProject extensionProject, IModelObjectAdopter modelObjectAdopter,
        List<FormUpdateCandidate> candidates)
    {
        IProject project = extensionProject.getProject();
        if (project == null)
        {
            logInfo("Resource fallback skipped: extension project has no workspace project"); //$NON-NLS-1$
            return;
        }

        ResourceSet resourceSet = new ResourceSetImpl();
        int[] counters = new int[4];
        try
        {
            project.accept((IResourceVisitor)resource -> {
                if (resource.getType() != IResource.FILE || !"mdo".equalsIgnoreCase(resource.getFileExtension())) //$NON-NLS-1$
                    return true;

                counters[0]++;
                URI uri = URI.createPlatformResourceURI(resource.getFullPath().toString(), true);
                logInfo("Resource fallback loading: {0}", uri); //$NON-NLS-1$
                try
                {
                    Resource emfResource = resourceSet.getResource(uri, true);
                    counters[1]++;
                    for (EObject root : emfResource.getContents())
                    {
                        collectResourceForm(root, extensionProject, modelObjectAdopter, candidates, counters);
                        for (Iterator<EObject> iterator = root.eAllContents(); iterator.hasNext();)
                            collectResourceForm(iterator.next(), extensionProject, modelObjectAdopter, candidates, counters);
                    }
                }
                catch (RuntimeException e)
                {
                    counters[3]++;
                    logInfo("Resource fallback failed for {0}: {1}", uri, e.toString()); //$NON-NLS-1$
                }
                return true;
            });
        }
        catch (CoreException e)
        {
            logError(e);
        }
        logInfo("Resource fallback counters. mdoFiles: {0}, loaded: {1}, basicForms: {2}, failures: {3}, candidates: {4}", //$NON-NLS-1$
            counters[0], counters[1], counters[2], counters[3], candidates.size());
    }

    private void collectResourceForm(EObject object, IExtensionProject extensionProject,
        IModelObjectAdopter modelObjectAdopter, List<FormUpdateCandidate> candidates, int[] counters)
    {
        if (!(object instanceof BasicForm))
            return;

        counters[2]++;
        BasicForm adoptedMdForm = (BasicForm)object;
        Form adoptedForm = getForm(adoptedMdForm);
        logInfo("Resource fallback BasicForm. name: {0}, class: {1}, formResolved: {2}, resource: {3}", //$NON-NLS-1$
            adoptedMdForm.getName(), adoptedMdForm.eClass().getName(), adoptedForm != null,
            adoptedMdForm.eResource() != null ? adoptedMdForm.eResource().getURI() : "null"); //$NON-NLS-1$
        if (adoptedForm != null)
        {
            collectForm(adoptedForm, extensionProject, modelObjectAdopter, candidates);
            return;
        }

        collectBasicForm(adoptedMdForm, extensionProject, modelObjectAdopter, candidates);
    }

    private void collectBasicForm(BasicForm adoptedMdForm, IExtensionProject extensionProject,
        IModelObjectAdopter modelObjectAdopter, List<FormUpdateCandidate> candidates)
    {
        String formName = adoptedMdForm.getName() != null ? adoptedMdForm.getName() : ""; //$NON-NLS-1$
        String ownerName = getOwnerName(adoptedMdForm);
        BasicForm sourceMdForm = null;
        try
        {
            sourceMdForm = modelObjectAdopter.getSource(adoptedMdForm);
        }
        catch (RuntimeException e)
        {
            logInfo("BasicForm source lookup failed. Owner: {0}, form: {1}, error: {2}", ownerName, formName, //$NON-NLS-1$
                e.toString());
            return;
        }

        if (sourceMdForm == null)
        {
            logInfo("Skipped BasicForm without source metadata form. Owner: {0}, form: {1}", ownerName, formName); //$NON-NLS-1$
            return;
        }

        Form sourceForm = getForm(sourceMdForm);
        logInfo("BasicForm source found. Owner: {0}, form: {1}, sourceClass: {2}, sourceFormResolved: {3}", //$NON-NLS-1$
            ownerName, formName, sourceMdForm.eClass().getName(), sourceForm != null);
        if (sourceForm == null)
        {
            sourceForm = loadSiblingForm(sourceMdForm);
            logInfo("BasicForm sibling source form lookup. Owner: {0}, form: {1}, sourceFormResolved: {2}", //$NON-NLS-1$
                ownerName, formName, sourceForm != null);
        }
        if (sourceForm == null)
            return;

        boolean updatable = false;
        try
        {
            updatable = modelObjectAdopter.isUpdatable(sourceForm, extensionProject);
        }
        catch (RuntimeException e)
        {
            logInfo("BasicForm isUpdatable failed. Owner: {0}, form: {1}, error: {2}", ownerName, formName, //$NON-NLS-1$
                e.toString());
        }

        candidates.add(new FormUpdateCandidate(sourceForm, formName, ownerName));
        logInfo("Candidate added from BasicForm. Owner: {0}, form: {1}, updatable: {2}", ownerName, formName, //$NON-NLS-1$
            updatable);
    }

    private Form loadSiblingForm(BasicForm mdForm)
    {
        if (mdForm.eResource() == null || mdForm.eResource().getURI() == null || mdForm.getName() == null)
            return null;

        URI mdUri = mdForm.eResource().getURI();
        URI formUri = mdUri.trimSegments(1).appendSegment("Forms").appendSegment(mdForm.getName()).appendSegment("Form.form"); //$NON-NLS-1$ //$NON-NLS-2$
        try
        {
            Resource formResource = mdForm.eResource().getResourceSet().getResource(formUri, true);
            if (!formResource.getContents().isEmpty() && formResource.getContents().get(0) instanceof Form)
                return (Form)formResource.getContents().get(0);
        }
        catch (RuntimeException e)
        {
            logInfo("Sibling Form.form load failed. uri: {0}, error: {1}", formUri, e.toString()); //$NON-NLS-1$
        }
        return null;
    }

    private void collectForm(EObject object, IExtensionProject extensionProject, IModelObjectAdopter modelObjectAdopter,
        List<FormUpdateCandidate> candidates)
    {
        Form adoptedForm = null;
        if (object instanceof Form)
            adoptedForm = (Form)object;
        else if (object instanceof BasicForm)
            adoptedForm = getForm((BasicForm)object);

        if (adoptedForm == null)
            return;

        String formName = getFormName(adoptedForm);
        String ownerName = getOwnerName(adoptedForm);
        BasicForm adoptedMdForm = adoptedForm.getMdForm();
        BasicForm sourceMdForm = adoptedMdForm != null ? modelObjectAdopter.getSource(adoptedMdForm) : null;
        Form sourceForm = modelObjectAdopter.getSource(adoptedForm);
        if (sourceForm == null && sourceMdForm != null)
            sourceForm = getForm(sourceMdForm);

        if (adoptedMdForm == null)
        {
            logInfo("Skipped form without metadata form. Owner: {0}, form: {1}", ownerName, formName); //$NON-NLS-1$
            return;
        }
        if (sourceMdForm == null)
        {
            logInfo("Skipped form without source metadata form. Owner: {0}, form: {1}, mdClass: {2}", ownerName, //$NON-NLS-1$
                formName, adoptedMdForm.eClass().getName());
            return;
        }
        if (sourceForm == null)
        {
            logInfo("Skipped form without source form model. Owner: {0}, form: {1}, sourceMdClass: {2}", ownerName, //$NON-NLS-1$
                formName, sourceMdForm.eClass().getName());
            return;
        }

        boolean updatable = modelObjectAdopter.isUpdatable(sourceForm, extensionProject);
        logInfo(
            "Form checked. Owner: {0}, form: {1}, mdClass: {2}, sourceMdClass: {3}, sourceClass: {4}, updatable: {5}", //$NON-NLS-1$
            ownerName, formName, adoptedMdForm.eClass().getName(), sourceMdForm.eClass().getName(),
            sourceForm.eClass().getName(), updatable);

        candidates.add(new FormUpdateCandidate(sourceForm, formName, ownerName));
        logInfo("Candidate added. Owner: {0}, form: {1}, updatable: {2}", ownerName, formName, updatable); //$NON-NLS-1$
    }

    private boolean prepareBatchUpdate(ExecutionEvent event)
    {
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
        if (window == null)
            window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window != null && !window.getWorkbench().saveAllEditors(true))
        {
            logInfo("Update cancelled because saveAllEditors returned false"); //$NON-NLS-1$
            return false;
        }
        if (window != null)
        {
            IWorkbenchPage page = window.getActivePage();
            if (page != null)
            {
                logInfo("Closing all editors before batch update"); //$NON-NLS-1$
                page.closeAllEditors(true);
            }
        }
        return true;
    }

    private IExtensionProject getSelectedExtensionProject(ExecutionEvent event, IV8ProjectManager v8ProjectManager)
    {
        ISelection selection = HandlerUtil.getCurrentSelection(event);
        logInfo("Current selection: {0}", selection != null ? selection.getClass().getName() : "null"); //$NON-NLS-1$ //$NON-NLS-2$
        if (selection instanceof IStructuredSelection)
        {
            Iterator<?> iterator = ((IStructuredSelection)selection).iterator();
            while (iterator.hasNext())
            {
                Object selected = iterator.next();
                logInfo("Selection element: {0}", selected != null ? selected.getClass().getName() : "null"); //$NON-NLS-1$ //$NON-NLS-2$
                IExtensionProject extensionProject = getExtensionProject(selected, v8ProjectManager);
                if (extensionProject != null)
                    return extensionProject;
            }
        }

        Collection<IExtensionProject> extensionProjects = v8ProjectManager.getProjects(IExtensionProject.class);
        logInfo("Fallback extension project count: {0}", extensionProjects.size()); //$NON-NLS-1$
        return extensionProjects.size() == 1 ? extensionProjects.iterator().next() : null;
    }

    private IExtensionProject getExtensionProject(Object selected, IV8ProjectManager v8ProjectManager)
    {
        IProject project = adapt(selected, IProject.class);
        if (project == null)
        {
            IResource resource = adapt(selected, IResource.class);
            if (resource != null)
                project = resource.getProject();
        }
        if (project != null)
        {
            logInfo("Selection adapted to project: {0}", project.getName()); //$NON-NLS-1$
            return asExtensionProject(v8ProjectManager.getProject(project));
        }

        EObject modelObject = getModelObject(selected);
        if (modelObject != null)
        {
            logInfo("Selection adapted to EObject: {0}", modelObject.eClass().getName()); //$NON-NLS-1$
            return asExtensionProject(v8ProjectManager.getProject(modelObject));
        }

        return null;
    }

    private IExtensionProject asExtensionProject(IV8Project project)
    {
        return project instanceof IExtensionProject ? (IExtensionProject)project : null;
    }

    private Form getForm(BasicForm basicForm)
    {
        AbstractForm abstractForm = basicForm.getForm();
        return abstractForm instanceof Form ? (Form)abstractForm : null;
    }

    private EObject getModelObject(Object selected)
    {
        if (selected instanceof EObject)
            return (EObject)selected;

        String[] methodNames = { "getModelObject", "getModel", "getEObject", "getObject", "getTarget" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        for (String methodName : methodNames)
        {
            Object value = invokeNoArg(selected, methodName);
            if (value instanceof EObject)
                return (EObject)value;
        }
        return null;
    }

    private String getFormName(Form form)
    {
        BasicForm mdForm = form.getMdForm();
        return mdForm != null && mdForm.getName() != null ? mdForm.getName() : ""; //$NON-NLS-1$
    }

    private String getOwnerName(Form form)
    {
        BasicForm mdForm = form.getMdForm();
        EObject owner = mdForm != null ? mdForm.eContainer() : form.eContainer();
        return getOwnerName(owner);
    }

    private String getOwnerName(BasicForm form)
    {
        return getOwnerName(form.eContainer());
    }

    private String getOwnerName(EObject owner)
    {
        while (owner != null)
        {
            Object name = invokeNoArg(owner, "getName"); //$NON-NLS-1$
            if (name instanceof String && !((String)name).isEmpty())
                return owner.eClass().getName() + "." + name; //$NON-NLS-1$
            owner = owner.eContainer();
        }
        return ""; //$NON-NLS-1$
    }

    private void showInfo(ExecutionEvent event, String message)
    {
        Shell shell = HandlerUtil.getActiveShell(event);
        MessageDialog.openInformation(shell, valueOrDefault(Messages.UpdateFormHandler_Title, "Form Update"), //$NON-NLS-1$
            valueOrDefault(message, "")); //$NON-NLS-1$
    }

    private String format(String pattern, String fallbackPattern, Object... arguments)
    {
        return MessageFormat.format(valueOrDefault(pattern, fallbackPattern), arguments);
    }

    private String valueOrDefault(String value, String fallback)
    {
        return value != null ? value : fallback;
    }

    private void logError(Throwable throwable)
    {
        Status status = new Status(IStatus.ERROR, PLUGIN_ID, throwable.getMessage(), throwable);
        StatusManager.getManager().handle(status, StatusManager.LOG);
        Platform.getLog(getClass()).log(status);
    }

    private void logInfo(String pattern, Object... arguments)
    {
        if (!DEBUG_LOGGING)
            return;

        Status status = new Status(IStatus.INFO, PLUGIN_ID, "[debug] " + format(pattern, pattern, arguments)); //$NON-NLS-1$
        StatusManager.getManager().handle(status, StatusManager.LOG);
        Platform.getLog(getClass()).log(status);
    }

    private String getProjectName(IExtensionProject extensionProject)
    {
        IProject project = extensionProject.getProject();
        return project != null ? project.getName() : String.valueOf(extensionProject);
    }

    private Object invokeNoArg(Object target, String methodName)
    {
        if (target == null)
            return null;

        try
        {
            Method method = target.getClass().getMethod(methodName);
            if (method.getParameterCount() == 0)
                return method.invoke(target);
        }
        catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException e)
        {
            return null;
        }
        return null;
    }

    private <T> T adapt(Object object, Class<T> type)
    {
        if (type.isInstance(object))
            return type.cast(object);
        if (object instanceof IAdaptable)
        {
            Object adapted = ((IAdaptable)object).getAdapter(type);
            if (type.isInstance(adapted))
                return type.cast(adapted);
        }
        return null;
    }
}
