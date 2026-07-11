import {makeStyles, tokens} from '@fluentui/react-components';

export const useWorkflowListControlsStyles = makeStyles({
    root: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalS,
        paddingBottom: tokens.spacingVerticalS,
        background: tokens.colorNeutralBackground1,
        paddingInline: tokens.spacingHorizontalS,
        boxSizing: 'border-box',
    },
    controlsRow: {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: tokens.spacingHorizontalS,
        width: '100%',
        '@media screen and (max-width: 600px)': {
            alignItems: 'stretch',
            flexDirection: 'column',
        },
    },
    leftControls: {
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
        width: '420px',
        maxWidth: '100%',
        minWidth: 0,
        '@media screen and (max-width: 600px)': {
            flex: 1,
            width: 'auto',
        },
    },
    filterPopover: {
        padding: tokens.spacingHorizontalS,
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalSNudge,
        minWidth: '180px',
        maxWidth: '260px',
    },
    filterList: {
        minHeight: '48px',
        maxHeight: '220px',
        overflowY: 'auto',
        display: 'flex',
        flexDirection: 'column',
    },
    activeTagsRow: {
        display: 'flex',
        alignItems: 'center',
        gap: tokens.spacingHorizontalXS,
        flexWrap: 'wrap',
    },
    emptyText: {
        color: tokens.colorNeutralForeground3,
        padding: `${tokens.spacingVerticalXS} ${tokens.spacingHorizontalS}`,
    },
});
