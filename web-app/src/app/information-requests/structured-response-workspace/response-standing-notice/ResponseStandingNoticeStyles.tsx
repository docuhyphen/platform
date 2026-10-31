import {makeStyles, tokens} from "@fluentui/react-components";

export const useResponseStandingNoticeStyles = makeStyles({
    note: {
        color: tokens.colorNeutralForeground2,
        overflowWrap: "anywhere",
    },
    reconfirm: {
        display: "flex",
        alignItems: "center",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
        paddingTop: tokens.spacingVerticalS,
        paddingBottom: tokens.spacingVerticalS,
        paddingLeft: tokens.spacingHorizontalM,
        paddingRight: tokens.spacingHorizontalM,
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground3,
    },
});
