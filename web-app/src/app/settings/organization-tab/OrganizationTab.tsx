import {Spinner} from "@fluentui/react-components";
import {useOrganizationTabStyles} from "./OrganizationTabStyles.tsx";
import {useOrganization} from "./useOrganization.ts";
import OrganizationPendingReview from "./organization-pending-review/OrganizationPendingReview.tsx";
import OrganizationSubTabs from "./organization-sub-tabs/OrganizationSubTabs.tsx";
import OrganizationEmptyState from "./organization-empty-state/OrganizationEmptyState.tsx";

const OrganizationTab = () =>
{
    const styles = useOrganizationTabStyles();
    const {organization, fetchingOrganization, error, getOrganization} = useOrganization();

    return <>
        {fetchingOrganization &&
            <div id={"organization-tab-div-1"}
                 className={styles.loadingWrapper}>
                <Spinner
                    id={"organization-tab-spinner-1"}
                    label="Loading"
                    size={"small"}
                />
            </div>
        }

        {error && (
            <div id={"organization-tab-div-2"}
                 className={styles.errorWrapper}>
                {error}
            </div>
        )}

        {organization && !fetchingOrganization && !organization.isActive && (
            <OrganizationPendingReview organization={organization}/>
        )}

        {organization && !fetchingOrganization && organization.isActive && (
            <OrganizationSubTabs organization={organization}/>
        )}

        {!organization && !fetchingOrganization && (
            <OrganizationEmptyState onOrganizationRegistered={() => getOrganization()}/>
        )}
    </>
}

export default OrganizationTab;
