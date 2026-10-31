import {makeStyles, tokens} from "@fluentui/react-components";

export const useClockHistoryPanelStyles = makeStyles({
    list: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        margin: 0,
        padding: 0,
        listStyleType: "none",
    },
});
