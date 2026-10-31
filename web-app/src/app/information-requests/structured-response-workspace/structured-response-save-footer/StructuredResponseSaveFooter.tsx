import {Button, Spinner, Text} from "@fluentui/react-components";
import {SaveRegular} from "@fluentui/react-icons";
import {RefusedResponseItem} from "../responseRefusalItems.ts";
import StructuredResponseRefusalItems from "../structured-response-refusal-items/StructuredResponseRefusalItems.tsx";
import {useStructuredResponseSaveFooterStyles} from "./StructuredResponseSaveFooterStyles.tsx";

interface Props {
    busy: boolean;
    readOnly: boolean;
    error: string | null;
    refusedItems: RefusedResponseItem[];
    status: string;
    onSave: () => void;
}

const StructuredResponseSaveFooter = ({busy, readOnly, error, refusedItems, status, onSave}: Props) => {
    const styles = useStructuredResponseSaveFooterStyles();
    const saveIcon = busy
        ? (
            <Spinner id="information-request-response-save-spinner"
                     size="tiny"/>
        )
        : <SaveRegular/>;

    return (
        <>
            {error && (
                <div id="information-request-response-save-error"
                     role="alert"
                     className={styles.error}>
                    <Text id="information-request-response-save-error-text">
                        {error}
                    </Text>
                    <StructuredResponseRefusalItems items={refusedItems}/>
                </div>
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
                        icon={saveIcon}
                        disabled={busy || readOnly}
                        onClick={onSave}>
                    Save responses
                </Button>
            </div>
        </>
    );
};

export default StructuredResponseSaveFooter;
