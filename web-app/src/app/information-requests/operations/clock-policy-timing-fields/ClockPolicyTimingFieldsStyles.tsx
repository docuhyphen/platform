import {makeStyles, tokens} from "@fluentui/react-components";

export const useClockPolicyTimingFieldsStyles = makeStyles({
    durations: {
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))",
        gap: tokens.spacingHorizontalM,
    },
});
