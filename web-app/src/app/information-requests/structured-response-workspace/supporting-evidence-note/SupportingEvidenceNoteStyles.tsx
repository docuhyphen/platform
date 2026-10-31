import {makeStyles, tokens} from "@fluentui/react-components";

export const useSupportingEvidenceNoteStyles = makeStyles({
    note: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
        color: tokens.colorNeutralForeground3,
        overflowWrap: "anywhere",
    },
});
