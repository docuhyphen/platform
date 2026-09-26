import {makeStyles, tokens} from "@fluentui/react-components";

export const useReviewItemFindingsStyles = makeStyles({
    findings: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        margin: 0,
        padding: 0,
        listStyleType: "none",
    },
    finding: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
        paddingLeft: tokens.spacingHorizontalM,
        borderLeft: `${tokens.strokeWidthThick} solid ${tokens.colorPaletteMarigoldBorder2}`,
        minWidth: 0,
        overflowWrap: "anywhere",
    },
    header: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
    detail: {
        color: tokens.colorNeutralForeground3,
    },
});
