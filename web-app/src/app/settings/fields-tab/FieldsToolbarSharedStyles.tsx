import {GriffelStyle, tokens} from '@fluentui/react-components';

export const fieldsToolbarStyles = {
    descriptionText: {
        color: tokens.colorNeutralForeground3,
    },
    searchRow: {
        display: 'flex',
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        gap: tokens.spacingHorizontalS,
        width: '100%',
        '@media screen and (max-width: 600px)': {
            alignItems: 'stretch',
            flexDirection: 'column',
        },
    },
    searchRowInputs: {
        display: 'flex',
        alignItems: 'center',
        gap: tokens.spacingVerticalS,
        flex: '0 1 auto',
        minWidth: 0,
        '@media screen and (max-width: 600px)': {
            width: '100%',
        },
    },
    searchField: {
        flex: '0 1 420px',
        minWidth: 0,
        width: '420px',
        maxWidth: '100%',
        '@media screen and (max-width: 600px)': {
            flex: 1,
            width: 'auto',
        },
    },
} satisfies Record<string, GriffelStyle>;

export const fieldsFilterSectionStyles = {
    filterSection: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalXS,
    },
    filterSectionTitle: {
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        color: tokens.colorNeutralForeground3,
        paddingLeft: tokens.spacingHorizontalXXS,
    },
    filterSectionList: {
        maxHeight: '160px',
        overflowY: 'auto',
        display: 'flex',
        flexDirection: 'column',
    },
} satisfies Record<string, GriffelStyle>;
