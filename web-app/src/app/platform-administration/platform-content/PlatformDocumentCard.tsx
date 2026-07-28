import {DocumentLibraryEntrySummaryDto} from "../../models/models.tsx";
import DocumentLibraryEntryCard from "../../settings/document-library-tab/DocumentLibraryEntryCard.tsx";

interface Props
{
    entry: DocumentLibraryEntrySummaryDto;
    onEdit: () => void;
    onUpload: () => void;
    onDownload: () => void;
    onPublish: () => void;
    onActivate: () => void;
    onDelete: () => void;
}

const PlatformDocumentCard = ({
    entry,
    onEdit,
    onUpload,
    onDownload,
    onPublish,
    onActivate,
    onDelete,
}: Props) => (
    <DocumentLibraryEntryCard
        entry={entry}
        canManage={true}
        showPublishToggle={true}
        onEdit={onEdit}
        onUpload={onUpload}
        onDownload={onDownload}
        onPublish={onPublish}
        onActivate={onActivate}
        onClone={() => undefined}
        onDelete={onDelete}/>
);

export default PlatformDocumentCard;
