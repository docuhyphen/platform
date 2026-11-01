import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDetailsTabContentStyles = makeStyles({
    documentsSectionContainer: {
        display: "flex",
        flex: 1,
        minHeight: 0,
        height: "auto",
        overflow: "hidden",
    },
    documentsSection: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        flex: "1",
        minWidth: "200px",
        minHeight: 0,
        overflow: "hidden",
        "@media (max-width: 768px)": {
            minWidth: 0,
        },
    },
    scrollableTabContent: {
        overflowY: "auto",
        overflowX: "hidden",
        scrollbarGutter: "stable",
    },
});
