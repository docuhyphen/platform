import {makeStyles, tokens} from "@fluentui/react-components";

export const useRecipientPreviewDialogStyles = makeStyles({
    section: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        paddingBottom: tokens.spacingVerticalM,
        borderBottom: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
    },
    list: {
        listStyleType: "none",
        marginTop: 0,
        marginBottom: 0,
        paddingLeft: 0,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
    },
    requirement: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
    },
    promptLine: {
        display: "flex",
        alignItems: "center",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
    },
    muted: {
        color: tokens.colorNeutralForeground2,
    },
});
