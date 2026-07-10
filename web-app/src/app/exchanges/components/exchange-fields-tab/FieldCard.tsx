import {Badge, Text} from '@fluentui/react-components';
import {ReactNode} from 'react';
import {FieldValueType} from '../../../models/models';
import {useExchangeFieldsTabStyles} from './ExchangeFieldsTabStyles';
import {getFieldTypeLabel} from './fieldLayoutUtils';

interface FieldCardProps
{
    id: string;
    title: string;
    description?: string;
    valueType: FieldValueType;
    required?: boolean;
    readOnly?: boolean;
    children: ReactNode;
}

const FieldCard = ({
    id,
    title,
    description,
    valueType,
    required = false,
    readOnly = false,
    children,
}: FieldCardProps) =>
{
    const styles = useExchangeFieldsTabStyles();

    return (
        <div id={id}
             className={styles.fieldCardItem}>
            <div className={styles.fieldCard}>
                <div className={styles.fieldCardHeader}>
                    <div className={styles.fieldCardTitleBlock}>
                        <Text className={styles.fieldLabel}>
                            {title}
                        </Text>
                        {description && (
                            <Text className={styles.fieldDescription}>
                                {description}
                            </Text>
                        )}
                    </div>
                    <div className={styles.fieldCardMeta}>
                        <Badge appearance="outline"
                               color="subtle"
                               size="small">
                            {getFieldTypeLabel(valueType)}
                        </Badge>
                        {required && (
                            <Badge appearance="tint"
                                   color="danger"
                                   size="small">
                                Required
                            </Badge>
                        )}
                        {readOnly && (
                            <Badge appearance="tint"
                                   color="warning"
                                   size="small">
                                Read only
                            </Badge>
                        )}
                    </div>
                </div>
                <div className={styles.fieldValueArea}>
                    {children}
                </div>
            </div>
        </div>
    );
};

export default FieldCard;
