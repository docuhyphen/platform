import {makeStyles, tokens} from "@fluentui/react-components";

export const useRecurrenceFormStyles = makeStyles({
    form: {
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))",
        alignItems: "end",
        gap: tokens.spacingHorizontalM,
    },
    submit: {
        justifySelf: "start",
    },
});
