import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDetailKeyValueGridStyles = makeStyles({
    keyValueGrid: {
        display: "grid",
        gridTemplateColumns: "minmax(180px, 240px) 1fr",
        columnGap: tokens.spacingHorizontalM,
        rowGap: tokens.spacingVerticalSNudge,
    },

    keyLabel: {
        color: tokens.colorNeutralForeground3,
    },
});
