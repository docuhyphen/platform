import {useState} from "react";
import {Text} from "@fluentui/react-components";
import {InformationRequestPartyDto, InformationRequestShareRoleKey} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import {shareRoleLabels} from "../../shared/informationRequestLabels.ts";
import RecipientPreviewContent from "../recipient-preview-content/RecipientPreviewContent.tsx";
import {useRecipientPreviewDialogStyles} from "./RecipientPreviewDialogStyles.tsx";

interface Props
{
    requestId: string;
    parties: InformationRequestPartyDto[];
    onDismiss: () => void;
}

const ANSWERING_ROLES = new Set([
    InformationRequestShareRoleKey.CONTRIBUTOR,
    InformationRequestShareRoleKey.PREPARER,
    InformationRequestShareRoleKey.ATTESTOR,
]);

const choiceLabel = (party: InformationRequestPartyDto): string =>
    `${shareRoleLabels[party.roleKey]}: ${party.label ?? "Unnamed party"}`;

const RecipientPreviewDialog = ({requestId, parties, onDismiss}: Props) =>
{
    const styles = useRecipientPreviewDialogStyles();
    const answering = parties.filter(party => party.active && ANSWERING_ROLES.has(party.roleKey));
    const [chosen, setChosen] = useState(answering[0]?.id ?? "");
    const partyId = answering.some(party => party.id === chosen) ? chosen : answering[0]?.id ?? "";

    return (
        <EditorDialog id={"information-request-recipient-preview"}
                      title={"Preview as recipient"}
                      readOnly={true}
                      wide={true}
                      onConfirm={onDismiss}
                      onDismiss={onDismiss}>
            {partyId === "" ? (
                <Text id={"information-request-recipient-preview-no-party"}>
                    Add a party who answers this request to preview what they will be asked.
                </Text>
            ) : (
                <div id={"information-request-recipient-preview-body"}
                     className={styles.body}>
                    <ChoiceSelect id={"information-request-recipient-preview-party"}
                                  label={"Preview for"}
                                  value={partyId}
                                  hint={"Each party sees only the questions they answer."}
                                  options={answering.map(party => ({value: party.id, label: choiceLabel(party)}))}
                                  onChange={setChosen}/>
                    <RecipientPreviewContent requestId={requestId}
                                             partyId={partyId}/>
                </div>
            )}
        </EditorDialog>
    );
};

export default RecipientPreviewDialog;
