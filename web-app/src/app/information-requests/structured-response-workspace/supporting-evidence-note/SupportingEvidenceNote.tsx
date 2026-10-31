import {Text} from "@fluentui/react-components";
import {useSupportingEvidenceNotes} from "../supportingEvidenceNotes.ts";
import {useSupportingEvidenceNoteStyles} from "./SupportingEvidenceNoteStyles.tsx";

interface Props {
    elementId: string;
    requirementId: string;
}

const SupportingEvidenceNote = ({elementId, requirementId}: Props) => {
    const styles = useSupportingEvidenceNoteStyles();
    const {supportedBy, supports} = useSupportingEvidenceNotes(requirementId);
    if (supportedBy.length === 0 && supports.length === 0) return null;
    const id = `information-request-supporting-evidence-${elementId}`;

    return (
        <div id={id}
             className={styles.note}>
            {supportedBy.length > 0 && (
                <Text id={`${id}-supported-by`}
                      size={200}>
                    {`Supported by: ${supportedBy.join("; ")}`}
                </Text>
            )}
            {supports.length > 0 && (
                <Text id={`${id}-supports`}
                      size={200}>
                    {`Supports: ${supports.join("; ")}`}
                </Text>
            )}
        </div>
    );
};

export default SupportingEvidenceNote;
