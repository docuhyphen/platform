import {Button} from "@fluentui/react-components";
import {OrganizationDirectoryEntry} from "../../../../../services/organizationTrust.ts";
import {useOrganizationSearchResultsStyles} from "./OrganizationSearchResultsStyles.tsx";

interface OrganizationSearchResultsProps
{
    results: OrganizationDirectoryEntry[];
    selectedId?: string;
    onSelect: (organization: OrganizationDirectoryEntry) => void;
}

const OrganizationSearchResults = ({results, selectedId, onSelect}: OrganizationSearchResultsProps) =>
{
    const styles = useOrganizationSearchResultsStyles();
    return (
        <div
            id={"trusted-organization-search-results"}
            className={styles.root}
        >
            {results.map(result => (
                <Button
                    id={`trusted-organization-search-result-${result.id}`}
                    key={result.id}
                    shape={"circular"}
                    appearance={"subtle"}
                    className={`${styles.result} ${selectedId === result.id ? styles.selected : ""}`}
                    onClick={() => onSelect(result)}
                >
                    {result.name}
                </Button>
            ))}
        </div>
    );
};

export default OrganizationSearchResults;
