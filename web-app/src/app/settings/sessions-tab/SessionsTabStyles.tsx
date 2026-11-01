import {makeStyles, tokens} from "@fluentui/react-components";

export const useSessionsTabStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
        width: "100%",
        minWidth: 0,
        height: "100%",
        maxWidth: "100%",
    },

    subtleText: {
        color: tokens.colorNeutralForeground3,
    },
});
