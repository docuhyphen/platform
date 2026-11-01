import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDetailsPaneStyles = makeStyles({
    detailsContainer: {
        position: "relative",
        flex: 1,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
        width: "480px",
        minHeight: 0,
        "@media (max-width: 768px)": {
            width: "100%",
            gap: tokens.spacingHorizontalS,
        },
    },
    detailsPaneHidden: {
        "@media (max-width: 768px)": {
            display: "none",
        },
    },
    detailsContent: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
        transition: "opacity 180ms ease",
        opacity: 1,
        flex: 1,
        minHeight: 0,
    },
    detailsContentLoading: {
        opacity: 0.56,
    },
});
