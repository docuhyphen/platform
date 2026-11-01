import {makeStyles, tokens} from "@fluentui/react-components";

export const useAuditEventDetailStyles = makeStyles({
    surface: {
        maxWidth: "640px",
        width: "100%",
    },
    body: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        paddingTop: tokens.spacingVerticalM,
        paddingBottom: tokens.spacingVerticalM
    },
    fieldLabel: {
        color: tokens.colorNeutralForeground3,
    },
    fieldValue: {
        wordBreak: "break-all",
    },
    cardList: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
    },
});
