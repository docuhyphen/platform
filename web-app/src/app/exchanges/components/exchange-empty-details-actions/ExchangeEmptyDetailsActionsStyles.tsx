import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeEmptyDetailsActionsStyles = makeStyles({
    inboxEmptyActions: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
        justifyContent: "center",
        flexWrap: "wrap"
    },
});
