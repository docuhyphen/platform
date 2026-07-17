import {Button, Select, Text} from "@fluentui/react-components";
import {useMemo, useState} from "react";
import {OrganizationTrustRelationship, OrganizationTrustStatus} from "../../../../services/organizationTrust.ts";
import {useTrustedRelationshipListStyles} from "./TrustedRelationshipListStyles.tsx";

interface TrustedRelationshipListProps
{
    relationships: OrganizationTrustRelationship[];
    selectedId?: string;
    onSelect: (relationshipId: string) => void;
}

const statusLabel = (relationship: OrganizationTrustRelationship): string =>
    relationship.effectivelySuspended && relationship.status === "ACTIVE" ? "SUSPENDED" : relationship.status;

const TrustedRelationshipList = ({relationships, selectedId, onSelect}: TrustedRelationshipListProps) =>
{
    const styles = useTrustedRelationshipListStyles();
    const [filter, setFilter] = useState<OrganizationTrustStatus | "ALL">("ALL");
    const filtered = useMemo(
        () => relationships.filter(relationship => filter === "ALL" || relationship.status === filter),
        [filter, relationships],
    );

    return (
        <section
            id={"trusted-organization-relationship-list"}
            className={styles.root}
        >
            <Select
                id={"trusted-organization-status-filter"}
                aria-label={"Filter Trusted Organizations by status"}
                value={filter}
                onChange={event => setFilter(event.target.value as OrganizationTrustStatus | "ALL")}
            >
                <option value={"ALL"}>All statuses</option>
                {(["PENDING", "ACTIVE", "REJECTED", "WITHDRAWN", "EXPIRED", "ENDED"] as const).map(status => (
                    <option
                        id={`trusted-organization-filter-${status.toLowerCase()}`}
                        key={status}
                        value={status}
                    >
                        {status.toLowerCase()}
                    </option>
                ))}
            </Select>
            <div
                id={"trusted-organization-relationship-items"}
                className={styles.list}
            >
                {filtered.map(relationship => (
                    <Button
                        id={`trusted-organization-${relationship.id}`}
                        key={relationship.id}
                        shape={"circular"}
                        appearance={"subtle"}
                        className={`${styles.item} ${selectedId === relationship.id ? styles.selected : ""}`}
                        onClick={() => onSelect(relationship.id)}
                    >
                        <span
                            id={`trusted-organization-${relationship.id}-summary`}
                            className={styles.itemContent}
                        >
                            <Text
                                id={`trusted-organization-${relationship.id}-name`}
                                weight={"semibold"}
                            >
                                {relationship.partnerOrganizationName}
                            </Text>
                            <Text
                                id={`trusted-organization-${relationship.id}-status`}
                                size={200}
                                className={styles.meta}
                            >
                                {statusLabel(relationship)}
                            </Text>
                        </span>
                    </Button>
                ))}
            </div>
        </section>
    );
};

export default TrustedRelationshipList;
