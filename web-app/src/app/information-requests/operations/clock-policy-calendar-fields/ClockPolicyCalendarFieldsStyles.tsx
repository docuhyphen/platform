import {makeStyles, tokens} from "@fluentui/react-components";

export const useClockPolicyCalendarFieldsStyles = makeStyles({
    days: {
        display: "flex",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
    },
    hours: {
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))",
        gap: tokens.spacingHorizontalM,
    },
});
