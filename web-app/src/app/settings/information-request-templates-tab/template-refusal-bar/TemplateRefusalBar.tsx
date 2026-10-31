import {Button, MessageBar, MessageBarActions, MessageBarBody} from "@fluentui/react-components";
import {DismissIcon} from "../../../components/IconBundles.tsx";
import {TemplateRefusal} from "../template-editor/useTemplateEditorLifecycle.ts";

interface TemplateRefusalBarProps
{
    refusal: TemplateRefusal;
    onShow: () => void;
    onKeepChanges: () => void;
    onOpenLatest: () => void;
    onDismiss: () => void;
}

const TemplateRefusalBar = ({refusal, onShow, onKeepChanges, onOpenLatest, onDismiss}: TemplateRefusalBarProps) => (
    <MessageBar id={"information-request-template-refusal"}
                intent={"error"}
                role={"alert"}>
        <MessageBarBody id={"information-request-template-refusal-body"}>{refusal.message}</MessageBarBody>
        <MessageBarActions id={"information-request-template-refusal-actions"}
                           containerAction={(
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
            {refusal.keepable && (
                <Button id={"information-request-template-refusal-keep-changes"}
                        appearance={"primary"}
                        shape={"circular"}
                        onClick={onKeepChanges}>
                    Keep my changes
                </Button>
            )}
            {refusal.stale && (
                <Button id={"information-request-template-refusal-open-latest"}
                        appearance={"secondary"}
                        shape={"circular"}
                        onClick={onOpenLatest}>
                    Open latest draft
                </Button>
            )}
        </MessageBarActions>
    </MessageBar>
);

export default TemplateRefusalBar;
