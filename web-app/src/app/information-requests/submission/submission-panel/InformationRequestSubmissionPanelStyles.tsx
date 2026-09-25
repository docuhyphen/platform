import {makeStyles, tokens} from "@fluentui/react-components";

export const useInformationRequestSubmissionPanelStyles = makeStyles({
    panel: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        padding: tokens.spacingVerticalL,
        backgroundColor: tokens.colorNeutralBackground1,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusLarge,
        minWidth: 0,
    },
    header: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-end",
        gap: tokens.spacingHorizontalM,
        flexWrap: "wrap",
    },
    stageField: {
        minWidth: "min(260px, 100%)",
    },
    actions: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalM,
        flexWrap: "wrap",
    },
    detail: {
        color: tokens.colorNeutralForeground3,
    },
});
