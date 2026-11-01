import {makeStyles, tokens} from '@fluentui/react-components';
import {fieldsCardStyles} from '../FieldsSharedStyles';

export const useSchemaCardStyles = makeStyles({
    card: fieldsCardStyles.card,
    cardMain: fieldsCardStyles.cardMain,
    cardFieldName: fieldsCardStyles.cardFieldName,
    subText: {
        color: tokens.colorNeutralForeground3,
        fontSize: tokens.fontSizeBase200,
        overflow: 'hidden',
        display: '-webkit-box',
        WebkitLineClamp: '2',
        WebkitBoxOrient: 'vertical',
    },
    codeKey: {
        fontFamily: 'monospace',
        fontSize: tokens.fontSizeBase100,
        color: tokens.colorNeutralForeground3,
    },
    schemaCardCol2: {
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'flex-end',
        justifyContent: 'space-between',
        flexShrink: 0,
        gap: tokens.spacingHorizontalXS,
    },
});
