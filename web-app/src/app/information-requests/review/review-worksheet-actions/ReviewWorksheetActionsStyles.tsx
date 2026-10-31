import {makeStyles, tokens} from "@fluentui/react-components";

export const useReviewWorksheetActionsStyles = makeStyles({
    actions: {
        display: "flex",
        alignItems: "center",
        justifyContent: "flex-end",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
        position: "sticky",
        bottom: 0,
        padding: tokens.spacingVerticalS,
        backgroundColor: tokens.colorNeutralBackground1,
        borderTop: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
    },
    status: {
        marginRight: "auto",
        color: tokens.colorNeutralForeground3,
    },
});
