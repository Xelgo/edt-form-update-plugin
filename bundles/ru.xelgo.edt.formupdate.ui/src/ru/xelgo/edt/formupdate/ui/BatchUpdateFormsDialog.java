package ru.xelgo.edt.formupdate.ui;

import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;

final class BatchUpdateFormsDialog
    extends TitleAreaDialog
{
    interface UpdateOperation
    {
        boolean prepare();

        void update(FormUpdateCandidate candidate, IProgressMonitor monitor) throws CoreException;
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss"); //$NON-NLS-1$

    private final List<FormUpdateCandidate> candidates;
    private final UpdateOperation updateOperation;
    private CheckboxTableViewer viewer;
    private ProgressBar progressBar;
    private Text logText;
    private org.eclipse.swt.widgets.Button updateButton;
    private org.eclipse.swt.widgets.Button closeButton;
    private boolean updating;

    BatchUpdateFormsDialog(Shell parentShell, List<FormUpdateCandidate> candidates, UpdateOperation updateOperation)
    {
        super(parentShell);
        this.candidates = candidates;
        this.updateOperation = updateOperation;
    }

    @Override
    public void create()
    {
        super.create();
        setTitle(valueOrDefault(Messages.BatchUpdateFormsDialog_Title, "Update Extension Forms")); //$NON-NLS-1$
        setMessage(valueOrDefault(Messages.BatchUpdateFormsDialog_Message,
            "Select extension forms to update from the source configuration.")); //$NON-NLS-1$
    }

    @Override
    protected Control createDialogArea(Composite parent)
    {
        Composite area = (Composite)super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NONE);
        GridDataFactory.fillDefaults().grab(true, true).applyTo(container);
        GridLayoutFactory.swtDefaults().numColumns(1).applyTo(container);

        viewer = CheckboxTableViewer.newCheckList(container, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
        Table table = viewer.getTable();
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        GridDataFactory.fillDefaults().grab(true, true).hint(780, 320).applyTo(table);

        createColumns();
        viewer.setContentProvider(ArrayContentProvider.getInstance());
        viewer.setInput(candidates);
        viewer.setAllChecked(true);

        Composite buttons = new Composite(container, SWT.NONE);
        GridDataFactory.fillDefaults().grab(true, false).applyTo(buttons);
        GridLayoutFactory.swtDefaults().numColumns(2).applyTo(buttons);

        org.eclipse.swt.widgets.Button selectAll = new org.eclipse.swt.widgets.Button(buttons, SWT.PUSH);
        selectAll.setText(valueOrDefault(Messages.BatchUpdateFormsDialog_SelectAll, "Select all")); //$NON-NLS-1$
        selectAll.addListener(SWT.Selection, event -> viewer.setAllChecked(true));

        org.eclipse.swt.widgets.Button deselectAll = new org.eclipse.swt.widgets.Button(buttons, SWT.PUSH);
        deselectAll.setText(valueOrDefault(Messages.BatchUpdateFormsDialog_DeselectAll, "Deselect all")); //$NON-NLS-1$
        deselectAll.addListener(SWT.Selection, event -> viewer.setAllChecked(false));

        progressBar = new ProgressBar(container, SWT.HORIZONTAL);
        GridDataFactory.fillDefaults().grab(true, false).applyTo(progressBar);

        logText = new Text(container, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
        GridDataFactory.fillDefaults().grab(true, false).hint(780, 130).applyTo(logText);

        return area;
    }

    private void createColumns()
    {
        TableViewerColumn statusColumn = new TableViewerColumn(viewer, SWT.CENTER);
        statusColumn.getColumn().setText(valueOrDefault(Messages.BatchUpdateFormsDialog_StatusColumn, "Status")); //$NON-NLS-1$
        statusColumn.getColumn().setWidth(80);
        statusColumn.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                FormUpdateCandidate candidate = (FormUpdateCandidate)element;
                switch (candidate.getStatus())
                {
                case RUNNING:
                    return "..."; //$NON-NLS-1$
                case SUCCESS:
                    return "✓"; //$NON-NLS-1$
                case FAILED:
                    return "✗"; //$NON-NLS-1$
                case PENDING:
                default:
                    return ""; //$NON-NLS-1$
                }
            }

            @Override
            public Color getForeground(Object element)
            {
                FormUpdateCandidate candidate = (FormUpdateCandidate)element;
                Display display = Display.getCurrent();
                if (candidate.getStatus() == FormUpdateCandidate.Status.SUCCESS)
                    return display.getSystemColor(SWT.COLOR_DARK_GREEN);
                if (candidate.getStatus() == FormUpdateCandidate.Status.FAILED)
                    return display.getSystemColor(SWT.COLOR_RED);
                return null;
            }
        });

        TableViewerColumn formColumn = new TableViewerColumn(viewer, SWT.LEFT);
        formColumn.getColumn().setText(valueOrDefault(Messages.BatchUpdateFormsDialog_FormColumn, "Form")); //$NON-NLS-1$
        formColumn.getColumn().setWidth(260);
        formColumn.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                return ((FormUpdateCandidate)element).getFormName();
            }
        });

        TableViewerColumn ownerColumn = new TableViewerColumn(viewer, SWT.LEFT);
        ownerColumn.getColumn().setText(valueOrDefault(Messages.BatchUpdateFormsDialog_OwnerColumn, "Object")); //$NON-NLS-1$
        ownerColumn.getColumn().setWidth(360);
        ownerColumn.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                return ((FormUpdateCandidate)element).getOwnerName();
            }
        });

        TableViewerColumn messageColumn = new TableViewerColumn(viewer, SWT.LEFT);
        messageColumn.getColumn().setText(valueOrDefault(Messages.BatchUpdateFormsDialog_MessageColumn, "Message")); //$NON-NLS-1$
        messageColumn.getColumn().setWidth(300);
        messageColumn.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                return ((FormUpdateCandidate)element).getMessage();
            }
        });
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent)
    {
        updateButton = createButton(parent, IDialogConstants.OK_ID,
            valueOrDefault(Messages.BatchUpdateFormsDialog_UpdateButton, "Update"), true); //$NON-NLS-1$
        closeButton = createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CLOSE_LABEL, false);
    }

    @Override
    protected void okPressed()
    {
        startUpdate();
    }

    @Override
    protected void cancelPressed()
    {
        if (!updating)
            super.cancelPressed();
    }

    private void startUpdate()
    {
        List<FormUpdateCandidate> selectedCandidates = getSelectedCandidates();
        if (selectedCandidates.isEmpty())
        {
            appendLog(valueOrDefault(Messages.UpdateFormHandler_NoSelection, "No forms were selected for update.")); //$NON-NLS-1$
            return;
        }
        if (!updateOperation.prepare())
        {
            appendLog(valueOrDefault(Messages.BatchUpdateFormsDialog_PrepareCancelled, "Update was cancelled.")); //$NON-NLS-1$
            return;
        }

        updating = true;
        updateButton.setEnabled(false);
        closeButton.setEnabled(false);
        viewer.getTable().setEnabled(false);
        progressBar.setMinimum(0);
        progressBar.setMaximum(selectedCandidates.size());
        progressBar.setSelection(0);
        appendLog(format(Messages.BatchUpdateFormsDialog_Started, "Started. Selected: {0}.", selectedCandidates.size())); //$NON-NLS-1$

        Thread worker = new Thread(() -> runUpdate(selectedCandidates), "EDT form update batch"); //$NON-NLS-1$
        worker.setDaemon(true);
        worker.start();
    }

    private void runUpdate(List<FormUpdateCandidate> selectedCandidates)
    {
        int updated = 0;
        int failed = 0;
        NullProgressMonitor monitor = new NullProgressMonitor();
        for (FormUpdateCandidate candidate : selectedCandidates)
        {
            setCandidateStatus(candidate, FormUpdateCandidate.Status.RUNNING,
                valueOrDefault(Messages.BatchUpdateFormsDialog_StatusRunning, "Updating")); //$NON-NLS-1$
            appendLog(format(Messages.UpdateFormHandler_Updating, "Updating extension form \"{0}\"", //$NON-NLS-1$
                candidate.getFormName()));
            try
            {
                updateOperation.update(candidate, monitor);
                updated++;
                setCandidateStatus(candidate, FormUpdateCandidate.Status.SUCCESS,
                    valueOrDefault(Messages.BatchUpdateFormsDialog_StatusSuccess, "Updated")); //$NON-NLS-1$
                appendLog(format(Messages.BatchUpdateFormsDialog_Updated, "Updated: {0}", candidate.getFormName())); //$NON-NLS-1$
            }
            catch (CoreException | RuntimeException e)
            {
                failed++;
                String message = getErrorMessage(e);
                setCandidateStatus(candidate, FormUpdateCandidate.Status.FAILED, message);
                appendLog(format(Messages.BatchUpdateFormsDialog_Failed, "Failed: {0}. {1}", candidate.getFormName(), //$NON-NLS-1$
                    message));
            }
            incrementProgress();
        }
        finishUpdate(updated, failed);
    }

    private List<FormUpdateCandidate> getSelectedCandidates()
    {
        List<FormUpdateCandidate> selectedCandidates = new ArrayList<>();
        Arrays.stream(viewer.getCheckedElements()).map(FormUpdateCandidate.class::cast).forEach(selectedCandidates::add);
        return selectedCandidates;
    }

    private void setCandidateStatus(FormUpdateCandidate candidate, FormUpdateCandidate.Status status, String message)
    {
        candidate.setStatus(status);
        candidate.setMessage(message);
        Display display = getShell().getDisplay();
        display.asyncExec(() -> {
            if (viewer != null && !viewer.getTable().isDisposed())
                viewer.refresh(candidate);
        });
    }

    private void incrementProgress()
    {
        Display display = getShell().getDisplay();
        display.asyncExec(() -> {
            if (progressBar != null && !progressBar.isDisposed())
                progressBar.setSelection(progressBar.getSelection() + 1);
        });
    }

    private void finishUpdate(int updated, int failed)
    {
        Display display = getShell().getDisplay();
        display.asyncExec(() -> {
            if (getShell() == null || getShell().isDisposed())
                return;
            updating = false;
            updateButton.setEnabled(true);
            closeButton.setEnabled(true);
            viewer.getTable().setEnabled(true);
            String summary = format(Messages.UpdateFormHandler_Summary, "Updated: {0}. Failed: {1}.", updated, failed); //$NON-NLS-1$
            setMessage(summary);
            appendLog(summary);
        });
    }

    private void appendLog(String message)
    {
        Display display = getShell().getDisplay();
        display.asyncExec(() -> {
            if (logText == null || logText.isDisposed())
                return;
            logText.append("[" + LocalTime.now().format(TIME_FORMAT) + "] " + message + System.lineSeparator()); //$NON-NLS-1$ //$NON-NLS-2$
        });
    }

    private String getErrorMessage(Throwable throwable)
    {
        Throwable current = throwable;
        while (current instanceof InvocationTargetException && ((InvocationTargetException)current).getCause() != null)
            current = ((InvocationTargetException)current).getCause();
        return current.getMessage() != null ? current.getMessage() : current.toString();
    }

    @Override
    protected Point getInitialSize()
    {
        return new Point(980, 650);
    }

    private String format(String pattern, String fallbackPattern, Object... arguments)
    {
        return MessageFormat.format(valueOrDefault(pattern, fallbackPattern), arguments);
    }

    private String valueOrDefault(String value, String fallback)
    {
        return value != null ? value : fallback;
    }
}
