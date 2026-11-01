import {MenuItem} from '@fluentui/react-components';
import {CopyIcon} from '../../../../../components/IconBundles.tsx';
import {DocumentLibraryEntrySummaryDto} from '../../../../../models/models.tsx';

interface DocumentLibraryEntryCloneItemsProps
{
    entry: DocumentLibraryEntrySummaryDto;
    onCloneToPersonal?: () => void;
    onCloneToOrg?: () => void;
}

const DocumentLibraryEntryCloneItems = ({entry, onCloneToPersonal, onCloneToOrg}: DocumentLibraryEntryCloneItemsProps) => (
    <>
        {onCloneToPersonal && (
            <MenuItem id={`doc-clone-personal-item-${entry.id}`}
                      icon={<CopyIcon/>}
                      onClick={onCloneToPersonal}>
                Clone to My Collection
            </MenuItem>
        )}
        {onCloneToOrg && (
            <MenuItem id={`doc-clone-org-item-${entry.id}`}
                      icon={<CopyIcon/>}
                      onClick={onCloneToOrg}>
                Clone to Organization
            </MenuItem>
        )}
    </>
);

export default DocumentLibraryEntryCloneItems;
