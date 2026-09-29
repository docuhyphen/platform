import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeInformationRequestsTabStyles = makeStyles({
    root: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        paddingTop: tokens.spacingVerticalM,
        paddingBottom: tokens.spacingVerticalM,
        minWidth: 0,
    },
    header: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalM,
    },
    list: {
        listStyleType: "none",
        marginTop: 0,
        marginBottom: 0,
        paddingLeft: 0,
        borderTop: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
    },
    empty: {
        color: tokens.colorNeutralForeground2,
    },
});
