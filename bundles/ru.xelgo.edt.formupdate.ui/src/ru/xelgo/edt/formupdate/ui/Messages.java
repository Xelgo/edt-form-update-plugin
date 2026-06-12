package ru.xelgo.edt.formupdate.ui;

import org.eclipse.osgi.util.NLS;

final class Messages
    extends NLS
{
    private static final String BUNDLE_NAME = "ru.xelgo.edt.formupdate.ui.messages"; //$NON-NLS-1$

    public static String UpdateFormHandler_CannotFindSource;
    public static String UpdateFormHandler_NotExtensionForm;
    public static String UpdateFormHandler_NotForm;
    public static String UpdateFormHandler_NoCandidates;
    public static String UpdateFormHandler_NoExtensionProject;
    public static String UpdateFormHandler_NoSelection;
    public static String UpdateFormHandler_NotUpdatable;
    public static String UpdateFormHandler_SelectExtensionMessage;
    public static String UpdateFormHandler_SelectExtensionTitle;
    public static String UpdateFormHandler_Searching;
    public static String UpdateFormHandler_Success;
    public static String UpdateFormHandler_Summary;
    public static String UpdateFormHandler_Title;
    public static String UpdateFormHandler_UpdateFailed;
    public static String UpdateFormHandler_Updating;
    public static String UpdateFormHandler_UpdatingBatch;

    public static String BatchUpdateFormsDialog_DeselectAll;
    public static String BatchUpdateFormsDialog_Failed;
    public static String BatchUpdateFormsDialog_FormColumn;
    public static String BatchUpdateFormsDialog_Message;
    public static String BatchUpdateFormsDialog_MessageColumn;
    public static String BatchUpdateFormsDialog_OwnerColumn;
    public static String BatchUpdateFormsDialog_PrepareCancelled;
    public static String BatchUpdateFormsDialog_SelectAll;
    public static String BatchUpdateFormsDialog_Started;
    public static String BatchUpdateFormsDialog_StatusColumn;
    public static String BatchUpdateFormsDialog_StatusRunning;
    public static String BatchUpdateFormsDialog_StatusSuccess;
    public static String BatchUpdateFormsDialog_Title;
    public static String BatchUpdateFormsDialog_Updated;
    public static String BatchUpdateFormsDialog_UpdateButton;

    static
    {
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages()
    {
    }
}
