import {makeStyles, tokens} from "@fluentui/react-components";

export const useVariableTokenPickerGroupStyles = makeStyles({
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
});
