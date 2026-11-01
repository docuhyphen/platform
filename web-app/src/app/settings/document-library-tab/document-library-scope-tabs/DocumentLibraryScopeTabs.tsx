import {Button, Tab, TabList} from '@fluentui/react-components';
import {AddIcon} from '../../../components/IconBundles.tsx';
import {DocumentLibraryListTab} from '../documentLibraryListTypes.ts';
import {documentLibraryTabLabels} from '../documentLibraryListFilters.ts';
import {useDocumentLibraryScopeTabsStyles} from './DocumentLibraryScopeTabsStyles.tsx';

interface DocumentLibraryScopeTabsProps
{
    activeTab: DocumentLibraryListTab;
    hasOrg: boolean;
    canCreate: boolean;
    onTabSelect: (tab: DocumentLibraryListTab) => void;
    onCreate: () => void;
}

const DocumentLibraryScopeTabs = ({activeTab, hasOrg, canCreate, onTabSelect, onCreate}: DocumentLibraryScopeTabsProps) =>
{
    const styles = useDocumentLibraryScopeTabsStyles();

    return (
        <div id={"document-library-tab-div-2"}
             className={styles.header}>
            <TabList id={"document-library-tab-tab-list-1"}
                     selectedValue={activeTab}
                     onTabSelect={(_, d) => onTabSelect(d.value as DocumentLibraryListTab)}>
                <Tab id="doc-tab-personal"
                     value="PERSONAL">
                    {documentLibraryTabLabels.PERSONAL}
                </Tab>
                {hasOrg && (
                    <Tab id="doc-tab-org"
                         value="ORG">
                        {documentLibraryTabLabels.ORG}
                    </Tab>
                )}
                <Tab id="doc-tab-app"
                     value="APP">
                    {documentLibraryTabLabels.APP}
                </Tab>
            </TabList>

            {canCreate && (
                <Button id="doc-create-btn"
                        icon={<AddIcon/>}
                        appearance="subtle"
                        shape="circular"
                        onClick={onCreate}>
                    Create Document
                </Button>
            )}
        </div>
    );
};

export default DocumentLibraryScopeTabs;
