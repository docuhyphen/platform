import {makeStyles, tokens} from "@fluentui/react-components";

export const useVariableTokenInputStyles = makeStyles({
    tokenChip: {
        cursor: "default",
        display: "inline-flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXXS,
        margin: `0 ${tokens.spacingHorizontalXXS}`,
    },
    removeIcon: {
        fontSize: "10px",
    },
    removeButton: {
        minWidth: 0,
        padding: `0 ${tokens.spacingHorizontalXXS}`,
        height: "16px",
    },
    pickerGroup: {
        marginBottom: tokens.spacingVerticalS,
    },
    pickerGroupLabel: {
        color: tokens.colorNeutralForeground3,
        display: "block",
        marginBottom: tokens.spacingVerticalXS,
        textTransform: "uppercase",
        letterSpacing: "0.5px",
    },
    pickerGroupList: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXXS,
    },
    pickerGroupButton: {
        justifyContent: "flex-start",
        gap: tokens.spacingHorizontalS,
        padding: `${tokens.spacingVerticalXS} ${tokens.spacingHorizontalSNudge}`,
    },
    pickerSurface: {
        padding: tokens.spacingHorizontalM,
        minWidth: "260px",
        maxWidth: "320px",
        maxHeight: "400px",
        overflowY: "auto",
    },
    pickerTitle: {
        marginBottom: tokens.spacingVerticalS,
    },
    pickerSearch: {
        marginBottom: tokens.spacingVerticalS,
        width: "100%",
    },
    root: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
    },
    inputWrapper: {
        position: "relative",
    },
    fullWidth: {
        width: "100%",
    },
    insertButton: {
        fontSize: "11px",
        padding: `0 ${tokens.spacingHorizontalXS}`,
        minWidth: 0,
    },
    preview: {
        padding: `${tokens.spacingVerticalSNudge} ${tokens.spacingHorizontalS}`,
        background: tokens.colorNeutralBackground2,
        borderRadius: tokens.borderRadiusMedium,
        fontSize: "12px",
        lineHeight: "1.8",
        wordBreak: "break-word",
    },
});
