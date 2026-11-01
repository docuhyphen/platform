import {Button, Text, mergeClasses} from "@fluentui/react-components";
import {CheckmarkCircle24Regular, ErrorCircle24Regular} from "@fluentui/react-icons";
import {useSpeakToSalesFeedbackStyles} from "./SpeakToSalesFeedbackStyles.tsx";

interface SpeakToSalesFeedbackProps
{
    status: "success" | "error";
    errorMessage?: string;
    onRetry?: () => void;
}

export function SpeakToSalesFeedback({status, errorMessage, onRetry}: SpeakToSalesFeedbackProps)
{
    const styles = useSpeakToSalesFeedbackStyles();

    if (status === "success")
    {
        return (
            <div id={"speak-to-sales-feedback-success"}
                 className={styles.feedback}>
                <CheckmarkCircle24Regular id={"speak-to-sales-feedback-success-icon"}
                                          className={mergeClasses(styles.feedbackIcon, styles.successIcon)}/>
                <Text id={"speak-to-sales-feedback-success-title"}
                      size={500}
                      weight="semibold">Enquiry received, thank you!</Text>
                <Text id={"speak-to-sales-feedback-success-message"}>
                    Our sales team will review your details and be in touch shortly
                    to arrange a personalised demo.
                </Text>
            </div>
        );
    }

    return (
        <div id={"speak-to-sales-feedback-error"}
             className={styles.feedback}>
            <ErrorCircle24Regular id={"speak-to-sales-feedback-error-icon"}
                                  className={mergeClasses(styles.feedbackIcon, styles.errorIcon)}/>
            <Text id={"speak-to-sales-feedback-error-title"}
                  size={500}
                  weight="semibold">Something went wrong</Text>
            <Text id={"speak-to-sales-feedback-error-message"}>{errorMessage}</Text>
            <Button id={"speak-to-sales-retry-btn"}
                    appearance="secondary"
                    shape="circular"
                    onClick={onRetry}>
                Try again
            </Button>
        </div>
    );
}
