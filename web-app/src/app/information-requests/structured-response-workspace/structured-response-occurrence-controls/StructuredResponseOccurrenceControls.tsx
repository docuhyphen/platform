import {useState} from "react";
import {Button, Text} from "@fluentui/react-components";
import {AddRegular, ArrowDownRegular, ArrowUpRegular, DeleteRegular} from "@fluentui/react-icons";
import {InformationRequestTemplateGroupDto} from "../../../models/models.tsx";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import {groupElementKey} from "../../shared/responseElementKeys.ts";
import {groupLabel} from "../responseAnswerState.ts";
import {useStructuredResponseOccurrenceControlsStyles} from "./StructuredResponseOccurrenceControlsStyles.tsx";

interface Props {
    occurrenceId: string;
    title: string;
    first: boolean;
    last: boolean;
    removable: boolean;
    childGroups: InformationRequestTemplateGroupDto[];
    fullChildGroupKeys: string[];
    disabled: boolean;
    onMove: (offset: -1 | 1) => void;
    onRemove: () => void;
    onAddChild: (groupKey: string) => void;
}

const StructuredResponseOccurrenceControls = ({
                                                  occurrenceId,
                                                  title,
                                                  first,
                                                  last,
                                                  removable,
                                                  childGroups,
                                                  fullChildGroupKeys,
                                                  disabled,
                                                  onMove,
                                                  onRemove,
                                                  onAddChild,
                                              }: Props) => {
    const styles = useStructuredResponseOccurrenceControlsStyles();
    const [confirmingRemoval, setConfirmingRemoval] = useState(false);
    const dialogId = `information-request-occurrence-remove-dialog-${occurrenceId}`;

    return (
        <div id={`information-request-occurrence-actions-${occurrenceId}`}
             className={styles.groupControls}>
            <Button id={`information-request-occurrence-up-${occurrenceId}`}
                    shape="circular"
                    icon={<ArrowUpRegular/>}
                    disabled={disabled || first}
                    aria-label={`Move ${title} up`}
                    onClick={() => onMove(-1)}/>
            <Button id={`information-request-occurrence-down-${occurrenceId}`}
                    shape="circular"
                    icon={<ArrowDownRegular/>}
                    disabled={disabled || last}
                    aria-label={`Move ${title} down`}
                    onClick={() => onMove(1)}/>
            <Button id={`information-request-occurrence-remove-${occurrenceId}`}
                    shape="circular"
                    icon={<DeleteRegular/>}
                    disabled={disabled || !removable}
                    aria-label={`Remove ${title}`}
                    onClick={() => setConfirmingRemoval(true)}/>
            {childGroups.map(childGroup => (
                <Button
                    id={`information-request-add-child-occurrence-${occurrenceId}-${groupElementKey(childGroup.groupKey)}`}
                    key={childGroup.groupKey}
                    shape="circular"
                    icon={<AddRegular/>}
                    aria-label={`Add ${groupLabel(childGroup.groupKey).toLowerCase()} to ${title}`}
                    disabled={disabled || fullChildGroupKeys.includes(childGroup.groupKey)}
                    onClick={() => onAddChild(childGroup.groupKey)}>
                    {`Add ${groupLabel(childGroup.groupKey).toLowerCase()}`}
                </Button>
            ))}
            {confirmingRemoval && (
                <EditorDialog id={dialogId}
                              title={`Remove ${title}?`}
                              confirmLabel={"Remove"}
                              confirmDisabled={disabled}
                              onConfirm={() => {
                                  setConfirmingRemoval(false);
                                  onRemove();
                              }}
                              onDismiss={() => setConfirmingRemoval(false)}>
                    <Text id={`${dialogId}-summary`}>
                        {`This removes ${title} and every entry nested in it, with their answers. The removal stays in the request history.`}
                    </Text>
                </EditorDialog>
            )}
        </div>
    );
};

export default StructuredResponseOccurrenceControls;
