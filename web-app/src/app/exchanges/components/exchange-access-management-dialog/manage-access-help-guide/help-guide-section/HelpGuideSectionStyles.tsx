import {makeStyles, tokens} from '@fluentui/react-components';

export const useHelpGuideSectionStyles = makeStyles({
    section: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalS,
    },

    sectionIntro: {
        color: tokens.colorNeutralForeground3,
        marginBottom: tokens.spacingVerticalXS,
    },
});
