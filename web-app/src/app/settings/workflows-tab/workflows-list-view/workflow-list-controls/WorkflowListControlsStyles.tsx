import {makeStyles, tokens} from '@fluentui/react-components';

export const useWorkflowListControlsStyles = makeStyles({
    root: {
        display: 'flex',
        flexDirection: 'column',
        gap: '8px',
        paddingBottom: '8px',
        background: tokens.colorNeutralBackground1,
        paddingInline: '0.5rem',
        boxSizing: 'border-box',
    },
    controlsRow: {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: '8px',
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
        padding: '8px',
        display: 'flex',
        flexDirection: 'column',
        gap: '6px',
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
        gap: '4px',
        flexWrap: 'wrap',
    },
    emptyText: {
        color: tokens.colorNeutralForeground3,
        padding: '4px 8px',
    },
});
