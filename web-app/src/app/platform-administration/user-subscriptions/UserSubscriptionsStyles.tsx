import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useUserSubscriptionsStyles = makeStyles({
    container: {display: "flex", flexDirection: "column", ...shorthands.gap(tokens.spacingVerticalM)},
    toolbar: {display: "flex", flexWrap: "wrap", ...shorthands.gap(tokens.spacingHorizontalS)},
    tableScroll: {overflowX: "auto"},
    table: {minWidth: "720px"},
    actions: {textAlign: "right"},
    actionButtons: {display: "flex", justifyContent: "flex-end", ...shorthands.gap(tokens.spacingHorizontalXS)},
    pagination: {display: "flex", justifyContent: "flex-end", alignItems: "center", ...shorthands.gap(tokens.spacingHorizontalS)},
    dialogBody: {width: "min(680px, calc(100vw - 32px))", maxHeight: "calc(100vh - 64px)"},
    dialogContent: {display: "flex", flexDirection: "column", overflowY: "auto", ...shorthands.gap(tokens.spacingVerticalM)},
    grid: {display: "grid", gridTemplateColumns: "1fr 1fr", ...shorthands.gap(tokens.spacingHorizontalM), "@media (max-width: 600px)": {gridTemplateColumns: "1fr"}},
    footer: {display: "flex", justifyContent: "flex-end", ...shorthands.gap(tokens.spacingHorizontalS)},
});
