import {makeStyles, tokens} from "@fluentui/react-components";

export const useEvidenceUploadRowStyles = makeStyles({
    uploadRow: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        alignItems: "flex-start",
    },
    note: {
        color: tokens.colorNeutralForeground3,
    },
    progress: {
        width: "100%",
    },
});
