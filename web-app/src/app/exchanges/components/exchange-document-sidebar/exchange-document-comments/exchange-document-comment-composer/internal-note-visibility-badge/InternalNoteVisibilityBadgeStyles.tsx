import {makeStyles, tokens} from "@fluentui/react-components";

export const useInternalNoteVisibilityBadgeStyles = makeStyles({
    badge: {
        position: "absolute",
        top: tokens.spacingVerticalM,
        right: tokens.spacingHorizontalS,
        zIndex: 1,
        maxWidth: "calc(100% - 24px)",
    },
});
