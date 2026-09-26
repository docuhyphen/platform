import {makeStyles, tokens} from "@fluentui/react-components";

export const useReviewItemDecisionControlsStyles = makeStyles({
    controls: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        paddingTop: tokens.spacingVerticalS,
        borderTop: `1px solid ${tokens.colorNeutralStroke2}`,
        minWidth: 0,
    },
    outcomes: {
        flexWrap: "wrap",
    },
    actions: {
        display: "flex",
        alignItems: "center",
        justifyContent: "flex-end",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
    hint: {
        color: tokens.colorPaletteMarigoldForeground2,
    },
});
