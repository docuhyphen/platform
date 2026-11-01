import {makeStyles, tokens} from '@fluentui/react-components';

export const useVariablesTabStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalS,
        width: '100%',
        height: '100%',
        minHeight: 0,
    },
    header: {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        background: tokens.colorNeutralBackground1,
        paddingBottom: tokens.spacingVerticalS,
        flexWrap: 'wrap',
        gap: tokens.spacingHorizontalS,
        paddingInline: tokens.spacingHorizontalS,
        boxSizing: 'border-box',
    },
    scrollableContent: {
        flex: 1,
        minHeight: 0,
        overflowY: 'auto',
        overflowX: 'hidden',
        overscrollBehavior: 'contain',
        paddingInline: tokens.spacingHorizontalS,
        boxSizing: 'border-box',
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalL,
    },
    toolbar: {
        display: 'flex',
        justifyContent: 'flex-end',
    },
});
