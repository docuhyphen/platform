import {makeStyles, tokens} from "@fluentui/react-components";

export const useRetentionScheduleCardStyles = makeStyles({
    card: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        padding: tokens.spacingVerticalM,
        borderRadius: tokens.borderRadiusMedium,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        backgroundColor: tokens.colorNeutralBackground1,
        minWidth: 0,
    },
    form: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
    },
    fields: {
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))",
        gap: tokens.spacingHorizontalM,
    },
    actions: {
        display: "flex",
        justifyContent: "flex-end",
    },
});
