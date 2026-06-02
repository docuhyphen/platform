import {makeStyles, tokens} from '@fluentui/react-components';

export const useManageAccessHelpGuideStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        gap: '8px',
    },

    intro: {
        color: tokens.colorNeutralForeground3,
        marginBottom: '4px',
    },

    section: {
        display: 'flex',
        flexDirection: 'column',
        gap: '8px',
    },

    sectionIntro: {
        color: tokens.colorNeutralForeground3,
        marginBottom: '4px',
    },

    roleCard: {
        display: 'flex',
        flexDirection: 'column',
        gap: '4px',
        padding: '8px 12px',
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground3,
    },

    roleHeader: {
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
    },

    constraintCard: {
        display: 'flex',
        flexDirection: 'column',
        gap: '2px',
        padding: '8px 12px',
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground3,
    },

    footerDivider: {
        marginTop: '4px',
    },

    footerNote: {
        color: tokens.colorNeutralForeground3,
        fontStyle: 'italic',
    },
});
