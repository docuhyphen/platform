import {makeStyles, tokens} from "@fluentui/react-components";

export const useEvidenceArtifactRowStyles = makeStyles({
    row: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        padding: tokens.spacingHorizontalM,
        borderRadius: tokens.borderRadiusMedium,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        backgroundColor: tokens.colorNeutralBackground2,
        minWidth: 0,
    },
    summary: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
    identity: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
        minWidth: 0,
    },
    fileName: {
        fontWeight: tokens.fontWeightSemibold,
        overflowWrap: "anywhere",
    },
    meta: {
        color: tokens.colorNeutralForeground3,
    },
    findings: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
        margin: 0,
        paddingLeft: tokens.spacingHorizontalL,
    },
    blocking: {
        color: tokens.colorPaletteRedForeground1,
    },
    advisory: {
        color: tokens.colorNeutralForeground2,
    },
    actions: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
});
