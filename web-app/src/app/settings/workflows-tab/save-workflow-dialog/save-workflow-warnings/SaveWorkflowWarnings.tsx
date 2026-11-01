import {MessageBar, MessageBarBody, Text} from "@fluentui/react-components";
import {useSaveWorkflowWarningsStyles} from "./SaveWorkflowWarningsStyles.tsx";

interface Props
{
    warnings: string[];
}

const SaveWorkflowWarnings = ({warnings}: Props) =>
{
    const styles = useSaveWorkflowWarningsStyles();

    return (
        <MessageBar intent="warning"
                    id="workflow-save-warnings">
            <MessageBarBody id={"save-workflow-dialog-message-bar-body-2"}>
                <Text id={"save-workflow-dialog-text-1"}
                      weight="semibold"
                      block>
                    Review these routing warnings before saving:
                </Text>
                <ul id={"save-workflow-dialog-ul-1"}
                    className={styles.warningList}>
                    {warnings.map((warning, i) => (
                        <li key={i}
                            id={`workflow-save-warning-${i}`}>{warning}</li>
                    ))}
                </ul>
            </MessageBarBody>
        </MessageBar>
    );
};

export default SaveWorkflowWarnings;
