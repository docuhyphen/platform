import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDetailRowStyles = makeStyles({
    labelValueRow: {
        display: "flex",
        flexDirection: "row",
        gap: tokens.spacingHorizontalS,
        alignItems: "flex-start",
        flexWrap: "wrap",
    },
    label: {
        color: tokens.colorNeutralForeground3,
        minWidth: "120px",
        flexShrink: 0,
    },
});
