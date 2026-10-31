import {makeStyles, tokens} from "@fluentui/react-components";

export const usePromotableAnswerRowStyles = makeStyles({
    row: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
        paddingTop: tokens.spacingVerticalS,
        paddingBottom: tokens.spacingVerticalS,
        borderBottom: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
    },
    text: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
        minWidth: 0,
        overflowWrap: "anywhere",
    },
    muted: {
        color: tokens.colorNeutralForeground2,
    },
});
