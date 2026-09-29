import {makeStyles, tokens} from "@fluentui/react-components";

export const useReviewAssignmentsPanelStyles = makeStyles({
    panel: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        padding: tokens.spacingVerticalM,
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground1,
    },
    header: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
    },
    list: {
        listStyleType: "none",
        marginTop: 0,
        marginBottom: 0,
        paddingLeft: 0,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
    },
    assignment: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
    },
    actions: {
        display: "flex",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalXS,
    },
    detail: {
        color: tokens.colorNeutralForeground2,
    },
});
