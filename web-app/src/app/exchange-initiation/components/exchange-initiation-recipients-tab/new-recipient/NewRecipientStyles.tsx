import {makeStyles, tokens} from "@fluentui/react-components";

export const useNewRecipientStyles = makeStyles({
    nameFields: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
        "@media (max-width: 640px)": {
            flexDirection: "column",
        },
    },
    nameField: {
        flex: 1,
    },
});
