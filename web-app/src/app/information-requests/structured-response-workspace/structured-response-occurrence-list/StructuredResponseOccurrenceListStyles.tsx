import {makeStyles, tokens} from "@fluentui/react-components";

export const useStructuredResponseOccurrenceListStyles = makeStyles({
    scrollRegion: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
        minHeight: 0,
        overflowY: "auto",
        overflowX: "hidden",
        scrollbarGutter: "stable",
        paddingRight: tokens.spacingHorizontalXS,
    },
});
