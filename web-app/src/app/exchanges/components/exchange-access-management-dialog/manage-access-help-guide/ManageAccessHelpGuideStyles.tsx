import {makeStyles, tokens} from '@fluentui/react-components';

export const useManageAccessHelpGuideStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalS,
    },

    intro: {
        color: tokens.colorNeutralForeground3,
        marginBottom: tokens.spacingVerticalXS,
    },

    footerDivider: {
        marginTop: tokens.spacingVerticalXS,
    },

    footerNote: {
        color: tokens.colorNeutralForeground3,
        fontStyle: 'italic',
    },
});
