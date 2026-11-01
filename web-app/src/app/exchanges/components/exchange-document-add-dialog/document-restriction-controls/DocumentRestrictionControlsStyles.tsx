import {makeStyles, tokens} from "@fluentui/react-components";

export const useDocumentRestrictionControlsStyles = makeStyles({
    documentRestriction: {
        display: 'flex',
        flexDirection: 'row',
        alignItems: 'center',
        gap: tokens.spacingHorizontalM,
        width: '100%',
        minWidth: 0,
        "@media (max-width: 768px)": {
            flexDirection: 'column',
            alignItems: 'stretch',
            gap: tokens.spacingHorizontalS,
        },
        "& > :last-child": {
            flex: 1,
            minWidth: 0,
            "@media (max-width: 768px)": {
                width: "100%",
            },
        },
    },
});
