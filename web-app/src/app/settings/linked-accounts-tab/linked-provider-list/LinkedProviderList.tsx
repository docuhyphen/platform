import LinkedProviderCard from "../linked-provider-card/LinkedProviderCard.tsx";
import {providerDefinitions} from "../providerDefinitions.ts";
import {useLinkedProviderListStyles} from "./LinkedProviderListStyles.tsx";

interface LinkedProviderListProps
{
    linkedProviders: string[];
    actionLoading: string | null;
    canUnlink: boolean;
    onLink: (provider: string) => void;
    onUnlink: (provider: string) => void;
}

const LinkedProviderList = (
    {
        linkedProviders,
        actionLoading,
        canUnlink,
        onLink,
        onUnlink,
    }: LinkedProviderListProps,
) =>
{
    const styles = useLinkedProviderListStyles();

    return (
        <div
            id={"linked-accounts-provider-list"}
            className={styles.providerList}>
            {providerDefinitions.map((providerCard) =>
            {
                const linked = linkedProviders.includes(providerCard.provider);

                return <LinkedProviderCard
                    id={`linked-provider-card-${providerCard.idPrefix}`}
                    key={providerCard.provider}
                    title={providerCard.title}
                    description={providerCard.description}
                    providerMark={providerCard.providerMark}
                    linked={linked}
                    isActionLoading={actionLoading === providerCard.provider}
                    canUnlink={canUnlink}
                    onLink={() => onLink(providerCard.provider)}
                    onUnlink={() => onUnlink(providerCard.provider)}
                />;
            })}
        </div>
    );
};

export default LinkedProviderList;
