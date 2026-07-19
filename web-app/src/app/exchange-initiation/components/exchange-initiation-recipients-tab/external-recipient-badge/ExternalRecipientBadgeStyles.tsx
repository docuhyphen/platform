import {tokens, makeStyles} from "@fluentui/react-components";

export const useExternalRecipientBadgeStyles = makeStyles({
    externalBadgeRow: {
        marginTop: tokens.spacingVerticalS,
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
    },
});
