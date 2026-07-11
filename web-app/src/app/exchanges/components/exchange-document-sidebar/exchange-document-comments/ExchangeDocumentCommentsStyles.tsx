import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useExchangeDocumentCommentsStyles = makeStyles({

    container: {
        display: "flex",
        flexDirection: "column",
        height: "100%",
        position: "relative"
    },

    list: {
        padding: `${tokens.spacingVerticalXS} ${tokens.spacingHorizontalS}`,
        display: "flex",
        flexDirection: "column",
        flexGrow: 1,
        gap: tokens.spacingHorizontalS,
        overflowY: "auto",
    },

    noComments: {
        textAlign: "center",
        color: tokens.colorNeutralForeground3,
        ...shorthands.padding(tokens.spacingHorizontalL)
    }
});
