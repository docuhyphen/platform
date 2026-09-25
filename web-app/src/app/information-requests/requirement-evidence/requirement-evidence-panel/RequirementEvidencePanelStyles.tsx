import {makeStyles, tokens} from "@fluentui/react-components";

export const useRequirementEvidencePanelStyles = makeStyles({
    panel: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        minWidth: 0,
    },
    header: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
    prompt: {
        fontWeight: tokens.fontWeightSemibold,
    },
    note: {
        color: tokens.colorNeutralForeground3,
    },
    findings: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
        margin: 0,
        paddingLeft: tokens.spacingHorizontalL,
        color: tokens.colorNeutralForeground2,
    },
    artifacts: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
    },
    uploadRow: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        alignItems: "flex-start",
    },
    progress: {
        width: "100%",
    },
});
