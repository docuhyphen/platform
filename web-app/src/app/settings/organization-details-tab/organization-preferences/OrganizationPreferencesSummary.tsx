import {Text} from "@fluentui/react-components";
import {OrganizationSettingsDto} from "../../../models/models.tsx";

interface OrganizationPreferencesSummaryProps
{
    settings: OrganizationSettingsDto;
}

const OrganizationPreferencesSummary = ({settings}: OrganizationPreferencesSummaryProps) => (
    <div id={"organization-preferences-summary"}>
        <Text id={"organization-details-tab-text-12"}
              size={300}>Trust request discovery: {settings.discoverableForTrustRequests ? 'Enabled' : 'Disabled'}</Text><br id={"organization-details-tab-br-2"}/>
        <Text id={"organization-details-tab-text-13"}
              size={300}>External sharing: {settings.allowExternalCustomerSharing !== false ? 'Enabled' : 'Disabled'}</Text><br id={"organization-details-tab-br-3"}/>
        <Text id={"organization-details-tab-text-14"}
              size={300}>Trusted organization required for B2B sharing: {settings.requireTrustedOrganizationForB2b ? 'Enabled' : 'Disabled'}</Text><br id={"organization-details-tab-br-4"}/>
        <Text id={"organization-details-tab-text-15"}
              size={300}>Profile updates: {settings.allowProfileUpdate ? 'Allowed' : 'Not allowed'}</Text><br id={"organization-details-tab-br-5"}/>
        <Text id={"organization-details-tab-text-16"}
              size={300}>Email updates: {settings.allowEmailUpdate ? 'Allowed' : 'Not allowed'}</Text>
    </div>
);

export default OrganizationPreferencesSummary;
