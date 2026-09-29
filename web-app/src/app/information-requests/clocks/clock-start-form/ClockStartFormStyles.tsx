import {makeStyles, tokens} from "@fluentui/react-components";

export const useClockStartFormStyles = makeStyles({
    form: {
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))",
        alignItems: "end",
        gap: tokens.spacingHorizontalM,
    },
    submit: {
        justifySelf: "start",
    },
});
