import {useEffect, useRef} from "react";
import {Checkbox, Text} from "@fluentui/react-components";
import {toFieldElementId} from "../../../exchanges/components/exchange-fields-tab/fieldLayoutUtils.ts";
import {HiddenClearConfirmation} from "../structuredResponseWorkspaceState.ts";
import {
    useStructuredResponseHiddenClearConfirmationsStyles
} from "./StructuredResponseHiddenClearConfirmationsStyles.tsx";

interface Props {
    confirmations: HiddenClearConfirmation[];
    confirmedRequirementIds: Set<string>;
    focusRequest: number;
    labelOf: (occurrencePath: string) => string;
    onToggle: (requirementId: string, confirmed: boolean) => void;
}

const StructuredResponseHiddenClearConfirmations = ({
                                                        confirmations,
                                                        confirmedRequirementIds,
                                                        focusRequest,
                                                        labelOf,
                                                        onToggle,
                                                    }: Props) => {
    const styles = useStructuredResponseHiddenClearConfirmationsStyles();
    const region = useRef<HTMLDivElement>(null);
    const shown = confirmations.length > 0;

    useEffect(() => {
        if (focusRequest > 0 && shown) region.current?.focus();
    }, [focusRequest, shown]);

    if (!shown) return null;

    return (
        <div id="information-request-hidden-clear-confirmations"
             ref={region}
             role={"group"}
             tabIndex={-1}
             aria-labelledby={"information-request-hidden-clear-confirmation-title"}
             className={styles.clearConfirmations}>
            <Text id="information-request-hidden-clear-confirmation-title"
                  className={styles.prompt}>
                Confirm hidden response clearing
            </Text>
            {confirmations.map(confirmation => (
                <Checkbox
                    id={`information-request-confirm-clear-requirement-${toFieldElementId(confirmation.requirementId)}`}
                    key={confirmation.requirementId}
                    checked={confirmedRequirementIds.has(confirmation.requirementId)}
                    onChange={(_, data) => onToggle(confirmation.requirementId, data.checked === true)}
                    label={labelOf(confirmation.occurrencePath)
                        ? `${confirmation.prompt} (${labelOf(confirmation.occurrencePath)})`
                        : confirmation.prompt}/>
            ))}
        </div>
    );
};

export default StructuredResponseHiddenClearConfirmations;
