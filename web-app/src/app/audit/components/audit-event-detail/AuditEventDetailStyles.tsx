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
    fieldGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
        gap: tokens.spacingVerticalS,
    },
    field: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
        minWidth: 0,
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
    card: {
        display: "flex",
        alignItems: "flex-start",
        gap: tokens.spacingHorizontalM,
        padding: tokens.spacingVerticalS,
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground3,
        minWidth: 0,
    },
    cardIcon: {
        flexShrink: 0,
        color: tokens.colorNeutralForeground3,
        marginTop: tokens.spacingVerticalXXS,
    },
    cardBody: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
        minWidth: 0,
        flexGrow: 1,
    },
    payloadList: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
    },
    payloadRow: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
        wordBreak: "break-all",
    },
    note: {
        color: tokens.colorNeutralForeground3,
    },
});
