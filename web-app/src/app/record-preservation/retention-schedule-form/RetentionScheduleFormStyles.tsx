import {makeStyles, tokens} from "@fluentui/react-components";

export const useRetentionScheduleFormStyles = makeStyles({
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
    note: {
        color: tokens.colorNeutralForeground3,
    },
    actions: {
        display: "flex",
        justifyContent: "flex-end",
    },
});
