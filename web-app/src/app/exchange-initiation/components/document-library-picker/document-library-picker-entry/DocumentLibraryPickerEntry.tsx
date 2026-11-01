import React from 'react';
import {Badge, Checkbox, Text} from '@fluentui/react-components';
import {DocumentLibraryEntrySummaryDto} from '../../../../models/models.tsx';
import {useExchangeInitiationStyles} from '../../../ExchangeInitiationStyles.tsx';

interface DocumentLibraryPickerEntryProps
{
    entry: DocumentLibraryEntrySummaryDto;
    selected: boolean;
    onToggle: (id: string) => void;
}

const DocumentLibraryPickerEntry: React.FC<DocumentLibraryPickerEntryProps> = ({entry, selected, onToggle}) =>
{
    const styles = useExchangeInitiationStyles();

    return (
        <div
            id={`doc-picker-item-${entry.id}`}
            onClick={() => onToggle(entry.id)}
            className={`${styles.docPickerEntry} ${selected ? styles.docPickerEntrySelected : ''}`}
        >
            <div id={"document-library-picker-div-4"}
                 className={styles.docPickerEntryHeader}>
                <Text
                    id={"document-library-picker-text-1"}
                    weight="semibold"
                    size={400}
                >
                    {entry.title}
                </Text>
                <Checkbox
                    id={`doc-picker-check-${entry.id}`}
                    checked={selected}
                    onChange={() => onToggle(entry.id)}
                    onClick={e => e.stopPropagation()}
                />
            </div>
            {entry.description && (
                <Text
                    id={"document-library-picker-text-2"}
                    size={200}
                    className={styles.docPickerEntryDescription}
                >
                    {entry.description}
                </Text>
            )}
            <div id={"document-library-picker-div-5"}
                 className={styles.docPickerEntryTagRow}>
                {entry.documentType && (
                    <Badge
                        id={"document-library-picker-badge-1"}
                        appearance="tint"
                        color="informative"
                        size="small"
                    >
                        {entry.documentType}
                    </Badge>
                )}
                {entry.generalTags.map(tag => (
                    <Badge
                        id={`document-library-picker-badge-2-${tag}`}
                        key={tag}
                        appearance="tint"
                        size="small"
                    >
                        {tag}
                    </Badge>
                ))}
            </div>
        </div>
    );
};

export default DocumentLibraryPickerEntry;
