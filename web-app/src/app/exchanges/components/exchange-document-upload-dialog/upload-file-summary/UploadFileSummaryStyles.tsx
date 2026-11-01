import {makeStyles, tokens} from "@fluentui/react-components";

export const useUploadFileSummaryStyles = makeStyles({
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
        "@media (max-width: 768px)": {
            gridTemplateColumns: "1fr",
            rowGap: tokens.spacingVerticalXXS,
        },
    },

    keyLabel: {
        color: tokens.colorNeutralForeground3,
    },
});
