import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeListEmptyStateStyles = makeStyles({
    emptyState: {
        display: "flex",
        width: "100%",
        height: "100%",
        flex: 1,
        justifyContent: "center",
        alignItems: "center",
        padding: `calc(${tokens.spacingHorizontalXXXL} + ${tokens.spacingHorizontalXXL} + ${tokens.spacingHorizontalXS})`,
        boxSizing: "border-box"
    },
});
