import {Button, MessageBar, MessageBarActions, MessageBarBody} from "@fluentui/react-components";
import {DismissIcon} from "../../../components/IconBundles.tsx";
import {TemplateRefusal} from "../template-editor/useTemplateEditorLifecycle.ts";

interface TemplateRefusalBarProps
{
    refusal: TemplateRefusal;
    onShow: () => void;
    onDismiss: () => void;
}

const TemplateRefusalBar = ({refusal, onShow, onDismiss}: TemplateRefusalBarProps) => (
    <MessageBar id={"information-request-template-refusal"}
                intent={"error"}
                role={"alert"}>
        <MessageBarBody>{refusal.message}</MessageBarBody>
        <MessageBarActions containerAction={(
            <Button id={"information-request-template-refusal-dismiss"}
                    appearance={"transparent"}
                    shape={"circular"}
                    icon={<DismissIcon/>}
                    aria-label={"Dismiss"}
                    onClick={onDismiss}/>
        )}>
            {refusal.target && (
                <Button id={"information-request-template-refusal-show"}
                        appearance={"secondary"}
                        shape={"circular"}
                        onClick={onShow}>
                    Show
                </Button>
            )}
        </MessageBarActions>
    </MessageBar>
);

export default TemplateRefusalBar;
