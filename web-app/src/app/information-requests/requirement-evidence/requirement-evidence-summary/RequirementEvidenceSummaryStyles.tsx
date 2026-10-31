import {makeStyles, tokens} from "@fluentui/react-components";

export const useRequirementEvidenceSummaryStyles = makeStyles({
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
});
