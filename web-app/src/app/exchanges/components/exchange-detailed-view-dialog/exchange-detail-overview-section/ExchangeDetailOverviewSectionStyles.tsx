import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDetailOverviewSectionStyles = makeStyles({
    titleRow: {
        display: "grid",
        gridTemplateColumns: "1fr auto",
        alignItems: "start",
        gap: tokens.spacingHorizontalS,
        minWidth: 0,
    },

    exchangeTitleText: {
        minWidth: 0,
        whiteSpace: "normal",
        overflowWrap: "anywhere",
        wordBreak: "break-word",
    },

    statusChip: {
        whiteSpace: "nowrap",
        alignSelf: "start",
    },
});
