import {Button, Spinner, Text} from "@fluentui/react-components";
import {SaveRegular} from "@fluentui/react-icons";
import {useInformationRequestStructuredResponseWorkspaceStyles} from "./InformationRequestStructuredResponseWorkspaceStyles.tsx";

interface Props
{
    busy: boolean;
    error: string | null;
    status: string;
    onSave: () => void;
}

const StructuredResponseSaveFooter = ({busy, error, status, onSave}: Props) =>
{
    const styles = useInformationRequestStructuredResponseWorkspaceStyles();

    return (
        <>
            {error && (
                <Text id="information-request-response-save-error"
                      role="alert"
                      className={styles.error}>
                    {error}
                </Text>
            )}
            <Text id="information-request-response-save-status"
                  role="status"
                  aria-live="polite">
                {status}
            </Text>
            <div id="information-request-response-actions"
                 className={styles.actions}>
                <Button id="information-request-response-save"
                        appearance="primary"
                        shape="circular"
                        icon={busy ? <Spinner size="tiny"/> : <SaveRegular/>}
                        disabled={busy}
                        onClick={onSave}>
                    Save responses
                </Button>
            </div>
        </>
    );
};

export default StructuredResponseSaveFooter;
