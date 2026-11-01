import {Text} from '@fluentui/react-components';
import {DocumentLibraryEntryCardProps} from './documentLibraryEntryCardTypes.ts';
import DocumentLibraryEntryBadges from './document-library-entry-badges/DocumentLibraryEntryBadges.tsx';
import DocumentLibraryEntryActions from './document-library-entry-actions/DocumentLibraryEntryActions.tsx';
import {useDocumentLibraryEntryCardStyles} from './DocumentLibraryEntryCardStyles.tsx';

const DocumentLibraryEntryCard = ({
    entry,
    actionsOnly = false,
    canManage,
    showPublishToggle,
    ...callbacks
}: DocumentLibraryEntryCardProps) =>
{
    const styles = useDocumentLibraryEntryCardStyles();

    const actions = (
        <DocumentLibraryEntryActions entry={entry}
                                     canManage={canManage}
                                     showPublishToggle={showPublishToggle}
                                     {...callbacks}/>
    );

    if (actionsOnly) return actions;

    return (
        <div id={`doc-lib-card-${entry.id}`}
             className={styles.card}>
            <div id={"document-library-entry-card-div-2"}
                 className={styles.cardBody}>
                <Text id={"document-library-entry-card-text-1"}
                      weight="semibold"
                      size={400}>
                    {entry.title}
                </Text>
                {entry.description && (
                    <Text id={"document-library-entry-card-text-2"}
                          size={200}
                          className={styles.descriptionText}>
                        {entry.description}
                    </Text>
                )}
                <DocumentLibraryEntryBadges entry={entry}
                                            showPublishToggle={showPublishToggle}/>
            </div>
            {actions}
        </div>
    );
};

export default DocumentLibraryEntryCard;
