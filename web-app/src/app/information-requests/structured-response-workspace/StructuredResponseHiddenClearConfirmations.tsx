import {Checkbox, Text} from "@fluentui/react-components";
import {toFieldElementId} from "../../exchanges/components/exchange-fields-tab/fieldLayoutUtils.ts";
import {HiddenClearConfirmation} from "./structuredResponseWorkspaceState.ts";
import {useInformationRequestStructuredResponseWorkspaceStyles} from "./InformationRequestStructuredResponseWorkspaceStyles.tsx";

interface Props
{
    confirmations: HiddenClearConfirmation[];
    confirmedRequirementIds: Set<string>;
    onToggle: (requirementId: string, confirmed: boolean) => void;
}

const StructuredResponseHiddenClearConfirmations = ({
    confirmations,
    confirmedRequirementIds,
    onToggle,
}: Props) =>
{
    const styles = useInformationRequestStructuredResponseWorkspaceStyles();
    if (confirmations.length === 0) return null;

    return (
        <div id="information-request-hidden-clear-confirmations"
             className={styles.clearConfirmations}>
            <Text id="information-request-hidden-clear-confirmation-title"
                  className={styles.prompt}>
                Confirm hidden response clearing
            </Text>
            {confirmations.map(confirmation => (
                <Checkbox id={`information-request-confirm-clear-requirement-${toFieldElementId(confirmation.requirementId)}`}
                          key={confirmation.requirementId}
                          checked={confirmedRequirementIds.has(confirmation.requirementId)}
                          onChange={(_, data) => onToggle(confirmation.requirementId, data.checked === true)}
                          label={`${confirmation.prompt} in ${confirmation.occurrencePath}`}/>
            ))}
        </div>
    );
};

export default StructuredResponseHiddenClearConfirmations;
