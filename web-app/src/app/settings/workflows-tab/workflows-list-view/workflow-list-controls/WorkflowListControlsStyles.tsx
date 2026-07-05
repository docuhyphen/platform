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
        gap: '8px',
        width: '100%',
        flexWrap: 'wrap',
    },
    searchField: {
        flex: '1 1 16rem',
        minWidth: '10rem',
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
