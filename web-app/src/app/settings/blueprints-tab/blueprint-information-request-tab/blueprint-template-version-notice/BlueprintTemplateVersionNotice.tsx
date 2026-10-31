import {MessageBar, MessageBarBody} from "@fluentui/react-components";
import {
    BlueprintInformationRequestTemplateVersionDto,
    InformationRequestTemplateStatus
} from "../../../../models/models.tsx";
import {pinnedVersionNotice} from "../blueprintPinnedVersion.ts";

interface Props {
    pinnedVersion: BlueprintInformationRequestTemplateVersionDto;
}

const BlueprintTemplateVersionNotice = ({pinnedVersion}: Props) => {
    const text = pinnedVersionNotice(pinnedVersion);
    if (!text) return null;

    return (
        <MessageBar id={"blueprint-information-request-version-notice"}
                    intent={pinnedVersion.status === InformationRequestTemplateStatus.RETIRED ? "warning" : "info"}>
            <MessageBarBody id={"blueprint-information-request-version-notice-text"}>
                {text}
            </MessageBarBody>
        </MessageBar>
    );
};

export default BlueprintTemplateVersionNotice;
