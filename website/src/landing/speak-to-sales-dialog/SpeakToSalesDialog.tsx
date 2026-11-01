import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Spinner,
} from "@fluentui/react-components";
import {Dismiss24Regular, Send24Regular} from "@fluentui/react-icons";
import {useState} from "react";
import type {ReactElement} from "react";
import {SpeakToSalesFeedback} from "./speak-to-sales-feedback/SpeakToSalesFeedback.tsx";
import {SpeakToSalesForm} from "./speak-to-sales-form/SpeakToSalesForm.tsx";
import {useSpeakToSalesDialogStyles} from "./SpeakToSalesDialogStyles.tsx";
import {useSpeakToSalesForm} from "./useSpeakToSalesForm.ts";

type SpeakToSalesDialogProps = {
    trigger: ReactElement;
};

export function SpeakToSalesDialog({trigger}: SpeakToSalesDialogProps)
{
    const styles = useSpeakToSalesDialogStyles();
    const [open, setOpen] = useState(false);
    const {
        form,
        errors,
        submitStatus,
        errorMessage,
        isValid,
        update,
        handleSubmit,
        markOpened,
        reset,
        retry,
    } = useSpeakToSalesForm();

    const handleOpenChange = (_: unknown, data: {open: boolean}) => {
        setOpen(data.open);
        if (data.open)
        {
            markOpened();
        }
        else
        {
            setTimeout(reset, 300);
        }
    };

    const isSubmitting = submitStatus === "submitting";
    const isEditing = submitStatus === "idle" || isSubmitting;

    return (
        <Dialog open={open}
                onOpenChange={handleOpenChange}>
            <DialogTrigger disableButtonEnhancement>
                {trigger}
            </DialogTrigger>

            <DialogSurface id={"speak-to-sales-dialog-surface"}
                           className={styles.surface}>
                <DialogBody id={"speak-to-sales-dialog-body"}>
                    <DialogTitle id={"speak-to-sales-dialog-title"}
                                 action={
                                     <DialogTrigger action="close">
                                         <Button id={"speak-to-sales-header-close-btn"}
                                                 appearance="subtle"
                                                 shape="circular"
                                                 aria-label="Close"
                                                 icon={<Dismiss24Regular id={"speak-to-sales-header-close-icon"}/>}/>
                                     </DialogTrigger>
                                 }>
                        Speak to Sales
                    </DialogTitle>

                    <DialogContent id={"speak-to-sales-dialog-content"}>
                        {submitStatus === "success" && <SpeakToSalesFeedback status="success"/>}
                        {submitStatus === "error" && (
                            <SpeakToSalesFeedback status="error"
                                                  errorMessage={errorMessage}
                                                  onRetry={retry}/>
                        )}
                        {isEditing && (
                            <SpeakToSalesForm form={form}
                                              errors={errors}
                                              update={update}
                                              isSubmitting={isSubmitting}/>
                        )}
                    </DialogContent>

                    <DialogActions id={"speak-to-sales-dialog-actions"}
                                   className={styles.actions}>
                        {submitStatus === "success" ? (
                            <DialogTrigger action="close">
                                <Button id={"speak-to-sales-close-btn"}
                                        appearance="primary"
                                        shape="circular">
                                    Close
                                </Button>
                            </DialogTrigger>
                        ) : submitStatus !== "error" && (
                            <>
                                <Button id={"speak-to-sales-submit-btn"}
                                        appearance="primary"
                                        shape="circular"
                                        icon={isSubmitting
                                            ? <Spinner id={"speak-to-sales-submit-spinner"}
                                                       size="tiny"/>
                                            : <Send24Regular id={"speak-to-sales-submit-icon"}/>}
                                        iconPosition="after"
                                        disabled={!isValid || isSubmitting}
                                        onClick={handleSubmit}>
                                    {isSubmitting ? "Sending..." : "Send enquiry"}
                                </Button>
                                <DialogTrigger action="close">
                                    <Button id={"speak-to-sales-cancel-btn"}
                                            appearance="secondary"
                                            shape="circular"
                                            disabled={isSubmitting}>
                                        Cancel
                                    </Button>
                                </DialogTrigger>
                            </>
                        )}
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
}
