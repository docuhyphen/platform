import {makeStyles, shorthands, tokens} from '@fluentui/react-components';

export const useLinkedAccountsTabStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        ...shorthands.gap(tokens.spacingHorizontalL),
        maxWidth: '760px',
    },

    headerBlock: {
        display: "flex",
        flexDirection: "column",
        ...shorthands.gap(tokens.spacingHorizontalS),
    },

    introText: {
        color: tokens.colorNeutralForeground3,
        maxWidth: "640px",
    },

    summaryPanel: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalL,
        ...shorthands.padding(tokens.spacingVerticalL, tokens.spacingHorizontalXL),
        ...shorthands.border("1px", "solid", tokens.colorNeutralStroke2),
        ...shorthands.borderRadius(tokens.borderRadiusLarge),
        backgroundColor: tokens.colorNeutralBackground2,
    },

    summaryCopy: {
        display: "flex",
        flexDirection: "column",
        ...shorthands.gap(tokens.spacingHorizontalXS),
    },

    summaryDetail: {
        color: tokens.colorNeutralForeground3,
    },

    summaryNote: {
        color: tokens.colorNeutralForeground2,
        maxWidth: "240px",
    },

});
