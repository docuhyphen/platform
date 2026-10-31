import {
    useInformationRequestCapabilities
} from "../../information-requests/capabilities/useInformationRequestCapabilities.ts";
import {scopeStandingNotice} from "../../information-requests/capabilities/scopeStandingText.ts";
import StandingNotice from "../../information-requests/shared/standing-notice/StandingNotice.tsx";
import {InformationRequestStandingReason} from "../../models/models.tsx";
import TemplateAdministration from "./template-administration/TemplateAdministration.tsx";
import {useInformationRequestTemplatesTabStyles} from "./InformationRequestTemplatesTabStyles.tsx";

const TYPED_ANSWERS_UNAVAILABLE = "This plan does not include typed answers. A Template can still ask for them, but a "
    + "request that does cannot be issued until the plan includes Business Fields and Schemas.";

const InformationRequestTemplatesTab = () =>
{
    const styles = useInformationRequestTemplatesTabStyles();
    const capabilities = useInformationRequestCapabilities();
    const respondOnly = capabilities !== null && !capabilities.featureIncluded && !capabilities.personalTemplatesAvailable;
    const notice = capabilities !== null
        && capabilities.newWorkUnavailableReason !== InformationRequestStandingReason.FEATURE_NOT_INCLUDED
        ? scopeStandingNotice(capabilities)
        : null;
    const typedAnswersNotice = capabilities !== null && capabilities.featureIncluded && !capabilities.typedAnswersAvailable
        ? {intent: "info" as const, text: TYPED_ANSWERS_UNAVAILABLE}
        : null;

    return (
        <div id={"settings-information-request-templates-tab"}
             className={styles.root}>
            <StandingNotice id={"information-request-templates-scope-standing"}
                            notice={notice}/>
            <StandingNotice id={"information-request-templates-typed-answers"}
                            notice={typedAnswersNotice}/>
            {!respondOnly && <TemplateAdministration capabilities={capabilities}/>}
        </div>
    );
};

export default InformationRequestTemplatesTab;
