import {makeStyles, tokens} from "@fluentui/react-components";

export const useClockPolicyVersionsStyles = makeStyles({
    versions: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        flexBasis: "100%",
        minWidth: 0,
        margin: 0,
        paddingLeft: tokens.spacingHorizontalL,
    },
    version: {
        color: tokens.colorNeutralForeground2,
        overflowWrap: "anywhere",
    },
});
