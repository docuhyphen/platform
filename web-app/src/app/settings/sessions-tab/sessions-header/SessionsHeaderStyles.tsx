import {makeStyles, tokens} from "@fluentui/react-components";

export const useSessionsHeaderStyles = makeStyles({
    header: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalM,
    },

    summaryBlock: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
    },

    subtleText: {
        color: tokens.colorNeutralForeground3,
    },
});
