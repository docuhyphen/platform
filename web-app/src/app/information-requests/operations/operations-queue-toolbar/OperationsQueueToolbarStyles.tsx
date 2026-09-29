import {makeStyles, tokens} from "@fluentui/react-components";

export const useOperationsQueueToolbarStyles = makeStyles({
    toolbar: {
        display: "flex",
        alignItems: "center",
        justifyContent: "flex-end",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalM,
    },
    selection: {
        marginRight: "auto",
        color: tokens.colorNeutralForeground2,
    },
});
