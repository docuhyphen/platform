import {useState} from "react";
import {Button, Text} from "@fluentui/react-components";
import CommunicationPickerDialog from "../../../components/communication-picker/CommunicationPickerDialog.tsx";
import {CommunicationScope, CommunicationSummaryDto, InformationRequestOwnerType} from "../../../models/models.tsx";
import {ClockPolicyForm, ClockPolicyNoticeField} from "../clock-policies/clockPolicyForm.ts";
import {useCommunicationNames} from "./useCommunicationNames.ts";
import {useClockPolicyNoticeFieldsStyles} from "./ClockPolicyNoticeFieldsStyles.tsx";

interface ClockPolicyNoticeFieldsProps {
    form: ClockPolicyForm;
    ownerType: InformationRequestOwnerType;
    onChange: (field: ClockPolicyNoticeField, value: string | undefined) => void;
}

const NOTICES: { field: ClockPolicyNoticeField; key: string; label: string; noun: string }[] = [
    {field: "reminderCommunicationId", key: "reminder", label: "Reminder notice", noun: "reminder notice"},
    {field: "overdueCommunicationId", key: "overdue", label: "Overdue notice", noun: "overdue notice"},
];

const scopesFor = (ownerType: InformationRequestOwnerType): CommunicationScope[] =>
    ownerType === InformationRequestOwnerType.ORGANIZATION ? ["ORG", "PLATFORM"] : ["PERSONAL", "PLATFORM"];

const ClockPolicyNoticeFields = ({form, ownerType, onChange}: ClockPolicyNoticeFieldsProps) => {
    const styles = useClockPolicyNoticeFieldsStyles();
    const [choosing, setChoosing] = useState<ClockPolicyNoticeField | null>(null);
    const {names, remember} = useCommunicationNames(NOTICES.map(notice => form[notice.field]));

    const choose = (communication: CommunicationSummaryDto) => {
        if (!choosing) return;
        remember(communication);
        onChange(choosing, communication.id);
    };

    return (
        <div id={"information-request-clock-policy-notices"}
             role={"group"}
             aria-labelledby={"information-request-clock-policy-notices-title"}
             className={styles.notices}>
            <Text id={"information-request-clock-policy-notices-title"}
                  weight={"semibold"}>
                Notice wording
            </Text>
            <Text id={"information-request-clock-policy-notices-hint"}
                  size={200}
                  className={styles.muted}>
                Reminder and overdue notices use the platform wording unless you choose a Communication.
            </Text>
            {NOTICES.map(notice => {
                const chosenId = form[notice.field];
                const id = `information-request-clock-policy-${notice.key}-notice`;
                return (
                    <div key={notice.key}
                         id={id}
                         className={styles.notice}>
                        <div id={`${id}-text`}
                             className={styles.text}>
                            <Text id={`${id}-label`}
                                  className={styles.label}>
                                {notice.label}
                            </Text>
                            <Text id={`${id}-choice`}>
                                {chosenId ? names[chosenId] ?? "Chosen Communication" : "Platform wording"}
                            </Text>
                        </div>
                        <div id={`${id}-actions`}
                             className={styles.actions}>
                            <Button id={`${id}-choose`}
                                    appearance={"secondary"}
                                    shape={"circular"}
                                    aria-label={`Choose the ${notice.noun} Communication`}
                                    onClick={() => setChoosing(notice.field)}>
                                Choose
                            </Button>
                            {chosenId && (
                                <Button id={`${id}-clear`}
                                        appearance={"subtle"}
                                        shape={"circular"}
                                        aria-label={`Use the platform wording for the ${notice.noun}`}
                                        onClick={() => onChange(notice.field, undefined)}>
                                    Use platform wording
                                </Button>
                            )}
                        </div>
                    </div>
                );
            })}
            <CommunicationPickerDialog open={choosing !== null}
                                       allowedScopes={scopesFor(ownerType)}
                                       selectedId={choosing ? form[choosing] : undefined}
                                       onClose={() => setChoosing(null)}
                                       onSelect={choose}/>
        </div>
    );
};

export default ClockPolicyNoticeFields;
