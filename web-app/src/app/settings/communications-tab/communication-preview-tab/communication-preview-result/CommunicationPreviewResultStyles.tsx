import {makeStyles, tokens} from "@fluentui/react-components";

export const useCommunicationPreviewResultStyles = makeStyles({
    previewResultsContainer: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
    },
    previewBox: {
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusXLarge,
        padding: tokens.spacingHorizontalM,
        background: tokens.colorNeutralBackground2,
    },
    previewSubjectLabel: {
        display: "block",
        marginBottom: tokens.spacingVerticalXS,
        color: tokens.colorNeutralForeground3,
    },
    previewBodyLabel: {
        display: "block",
        marginBottom: tokens.spacingVerticalS,
        color: tokens.colorNeutralForeground3,
    },
    previewPre: {
        fontFamily: "inherit",
        whiteSpace: "pre-wrap",
        wordBreak: "break-word",
        margin: 0,
    },
});
