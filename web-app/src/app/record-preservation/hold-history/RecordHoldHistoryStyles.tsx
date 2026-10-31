import {makeStyles, tokens} from "@fluentui/react-components";

export const useRecordHoldHistoryStyles = makeStyles({
    history: {
        flexBasis: "100%",
        minWidth: 0,
    },
    events: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
        margin: 0,
        paddingLeft: tokens.spacingHorizontalL,
    },
    event: {
        color: tokens.colorNeutralForeground3,
        overflowWrap: "anywhere",
    },
});
