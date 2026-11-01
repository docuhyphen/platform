import {Button, MessageBar, MessageBarActions, MessageBarBody, Spinner, Text} from "@fluentui/react-components";
import {getInformationRequestRecipientPreview} from "../../../../services/informationRequestAuthoringService.ts";
import {informationRequestRefusalMessage} from "../../shared/informationRequestRefusal.ts";
import {useServerRead} from "../../shared/useServerRead.ts";
import RecipientPreviewSections from "../recipient-preview-sections/RecipientPreviewSections.tsx";

interface Props {
    requestId: string;
    partyId: string;
}

const RecipientPreviewContent = ({requestId, partyId}: Props) => {
    const preview = useServerRead(
        `${requestId}:${partyId}`,
        () => getInformationRequestRecipientPreview(requestId, partyId),
        refusal => informationRequestRefusalMessage(refusal, "The preview could not be loaded."),
    );
    const shown = preview.value?.partyId === partyId ? preview.value : null;
    const sections = shown?.templateVersion.sections.filter(section => section.requirements.length > 0) ?? [];

    if (preview.error) {
        return (
            <MessageBar id={"information-request-recipient-preview-error"}
                        intent={"error"}
                        role={"alert"}>
                <MessageBarBody id={"information-request-recipient-preview-error-body"}>{preview.error}</MessageBarBody>
                <MessageBarActions id={"information-request-recipient-preview-error-actions"}>
                    <Button id={"information-request-recipient-preview-retry"}
                            shape={"circular"}
                            disabled={preview.loading}
                            onClick={() => { void preview.reload(); }}>Try again</Button>
                </MessageBarActions>
            </MessageBar>
        );
    }
    if (!shown) {
        return (
            <Spinner id={"information-request-recipient-preview-loading"}
                     label={"Loading preview"}/>
        );
    }
    if (sections.length === 0) {
        return (
            <Text id={"information-request-recipient-preview-nothing-asked"}>
                This party is not asked anything on this request.
            </Text>
        );
    }
    return <RecipientPreviewSections sections={sections}/>;
};

export default RecipientPreviewContent;
