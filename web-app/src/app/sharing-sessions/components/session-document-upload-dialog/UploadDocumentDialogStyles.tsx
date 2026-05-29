import {makeStyles, tokens} from "@fluentui/react-components";

export const useDocumentDialogStyles = makeStyles({
    contentContainer: {
        display: "flex",
        flexDirection: "column",
        gap: "12px",
        minWidth: "420px",
    },

    uploadContainer: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: "8px",
        padding: "8px 0",
    },

    hiddenInput: {
        display: "none",
    },

    fileInfoCard: {
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: "8px",
        padding: "12px",
        backgroundColor: tokens.colorNeutralBackground1,
        display: "flex",
        flexDirection: "column",
        gap: "6px",
    },

    keyValueRow: {
        display: "grid",
        gridTemplateColumns: "120px 1fr",
        columnGap: "10px",
    },

    keyLabel: {
        color: tokens.colorNeutralForeground3,
    },
});