import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeListBodyStyles = makeStyles({
    list: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
        overflow: "auto",
        height: "100%",
        paddingTop: tokens.spacingVerticalXXS,
        boxSizing: "border-box",
        scrollbarWidth: "thin",
        scrollbarColor: `${tokens.colorNeutralStroke1} transparent`,
        flex: 1,
        minWidth: "380px",
        "@media (max-width: 768px)": {
            minWidth: 0,
        },
        "&::-webkit-scrollbar": {
            width: "6px",
            height: "6px",
        },
        "&::-webkit-scrollbar-thumb": {
            backgroundColor: tokens.colorNeutralStroke1,
            borderRadius: tokens.borderRadiusCircular,
        },
        "&::-webkit-scrollbar-track": {
            backgroundColor: "transparent",
        },
    },
});
