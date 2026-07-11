import {makeStyles, tokens} from "@fluentui/react-components";

export const useDocumentDialogStyles = makeStyles({
    contentContainer: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
        minWidth: "420px",
        boxSizing: "border-box",
        // Phones: drop the 420px floor (smallest phones are ~320-360px),
        // and let the dialog surface shrink to fit the viewport.
        "@media (max-width: 768px)": {
            minWidth: 0,
            width: "100%",
        },
    },

    uploadContainer: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalS,
        padding: `${tokens.spacingVerticalS} 0`,
        flexWrap: "wrap",
    },

    hiddenInput: {
        display: "none",
    },

    fileInfoCard: {
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusXLarge,
        padding: tokens.spacingHorizontalM,
        backgroundColor: tokens.colorNeutralBackground1,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalSNudge,
        minWidth: 0,
        wordBreak: "break-word",
    },

    keyValueRow: {
        display: "grid",
        gridTemplateColumns: "120px 1fr",
        columnGap: tokens.spacingHorizontalMNudge,
        minWidth: 0,
        // Phones: stack the label above the value so long file names
        // don't squeeze the value column into one character per line.
        "@media (max-width: 768px)": {
            gridTemplateColumns: "1fr",
            rowGap: tokens.spacingVerticalXXS,
        },
    },

    keyLabel: {
        color: tokens.colorNeutralForeground3,
    },
});