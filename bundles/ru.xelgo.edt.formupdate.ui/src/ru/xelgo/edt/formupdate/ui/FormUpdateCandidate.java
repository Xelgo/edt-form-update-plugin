package ru.xelgo.edt.formupdate.ui;

import com._1c.g5.v8.dt.form.model.Form;

final class FormUpdateCandidate
{
    enum Status
    {
        PENDING,
        RUNNING,
        SUCCESS,
        FAILED
    }

    private final Form sourceForm;
    private final String formName;
    private final String ownerName;
    private Status status = Status.PENDING;
    private String message = ""; //$NON-NLS-1$

    FormUpdateCandidate(Form sourceForm, String formName, String ownerName)
    {
        this.sourceForm = sourceForm;
        this.formName = formName;
        this.ownerName = ownerName;
    }

    Form getSourceForm()
    {
        return sourceForm;
    }

    String getFormName()
    {
        return formName;
    }

    String getOwnerName()
    {
        return ownerName;
    }

    Status getStatus()
    {
        return status;
    }

    void setStatus(Status status)
    {
        this.status = status;
    }

    String getMessage()
    {
        return message;
    }

    void setMessage(String message)
    {
        this.message = message != null ? message : ""; //$NON-NLS-1$
    }
}
