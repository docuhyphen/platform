import {makeStyles, tokens} from '@fluentui/react-components';

export const useSchemaBindingRowStyles = makeStyles({
    row: {
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        padding: `${tokens.spacingVerticalMNudge} ${tokens.spacingHorizontalM}`,
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingVerticalS,
    },
    header: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        gap: tokens.spacingHorizontalS,
    },
    main: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingVerticalXS,
        minWidth: 0,
        flex: 1,
    },
    key: {
        fontFamily: tokens.fontFamilyMonospace,
        fontSize: tokens.fontSizeBase100,
        color: tokens.colorNeutralForeground3,
        overflowWrap: 'anywhere',
    },
    actions: {
        display: 'flex',
        alignItems: 'center',
        gap: tokens.spacingHorizontalXS,
        flexShrink: 0,
    },
    toggles: {
        display: 'flex',
        gap: tokens.spacingHorizontalL,
        alignItems: 'center',
        flexWrap: 'wrap',
    },
});
