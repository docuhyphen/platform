import {makeStyles, shorthands, tokens} from '@fluentui/react-components';

export const useLinkedAccountsTabStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        ...shorthands.gap('18px'),
        maxWidth: '760px',
    },

    headerBlock: {
        display: "flex",
        flexDirection: "column",
        ...shorthands.gap("8px"),
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
        gap: "16px",
        ...shorthands.padding("18px", "20px"),
        ...shorthands.border("1px", "solid", tokens.colorNeutralStroke2),
        ...shorthands.borderRadius(tokens.borderRadiusLarge),
        backgroundColor: tokens.colorNeutralBackground2,
    },

    summaryCopy: {
        display: "flex",
        flexDirection: "column",
        ...shorthands.gap("4px"),
    },

    summaryDetail: {
        color: tokens.colorNeutralForeground3,
    },

    summaryNote: {
        color: tokens.colorNeutralForeground2,
        maxWidth: "240px",
    },

    providerList: {
        display: "flex",
        flexDirection: "column",
        ...shorthands.gap("14px"),
    },
});
