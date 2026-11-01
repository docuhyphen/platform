import {makeStyles, tokens} from '@fluentui/react-components';
import {fieldsCardStyles} from './FieldsSharedStyles';

export const useFieldsTabStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        gap: "0",
        height: '100%',
        minHeight: 0,
    },
    stickyBlock: {
        background: tokens.colorNeutralBackground1,
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalS,
        paddingBottom: tokens.spacingVerticalM,
        flexShrink: 0,
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
    },
    cardGrid: fieldsCardStyles.cardGrid,
});
