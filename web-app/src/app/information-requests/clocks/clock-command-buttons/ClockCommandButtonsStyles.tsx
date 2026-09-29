import {makeStyles, tokens} from "@fluentui/react-components";

export const useClockCommandButtonsStyles = makeStyles({
    buttons: {
        display: "flex",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalXS,
    },
});
