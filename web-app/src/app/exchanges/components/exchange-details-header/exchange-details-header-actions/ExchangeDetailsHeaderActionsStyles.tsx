import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDetailsHeaderActionsStyles = makeStyles({
    actions: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
        alignSelf: "center",
        flexShrink: 0,
        "@media (max-width: 768px)": {
            gap: tokens.spacingHorizontalXXS,
        },
    },
});
