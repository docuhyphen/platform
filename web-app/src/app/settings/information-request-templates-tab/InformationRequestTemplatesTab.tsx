import {useInformationRequestCapabilities} from "../../information-requests/capabilities/useInformationRequestCapabilities.ts";
import {scopeStandingNotice} from "../../information-requests/capabilities/scopeStandingText.ts";
import StandingNotice from "../../information-requests/shared/standing-notice/StandingNotice.tsx";
import {InformationRequestStandingReason} from "../../models/models.tsx";
import TemplateAdministration from "./template-administration/TemplateAdministration.tsx";
import {useInformationRequestTemplatesTabStyles} from "./InformationRequestTemplatesTabStyles.tsx";

const InformationRequestTemplatesTab = () =>
{
    const styles = useInformationRequestTemplatesTabStyles();
    const capabilities = useInformationRequestCapabilities();
    const respondOnly = capabilities !== null && !capabilities.featureIncluded && !capabilities.personalTemplatesAvailable;
    const notice = capabilities !== null
        && capabilities.newWorkUnavailableReason !== InformationRequestStandingReason.FEATURE_NOT_INCLUDED
        ? scopeStandingNotice(capabilities)
        : null;

    return (
        <div id={"settings-information-request-templates-tab"}
             className={styles.root}>
            <StandingNotice id={"information-request-templates-scope-standing"}
                            notice={notice}/>
            {!respondOnly && <TemplateAdministration capabilities={capabilities}/>}
        </div>
    );
};

export default InformationRequestTemplatesTab;
