import {useState} from "react";
import {Button, Field, Input, Text} from "@fluentui/react-components";
import {CopyRegular} from "@fluentui/react-icons";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import {ShownAccessLink} from "../author-workspace/useAuthorWorkspace.ts";

interface Props
{
    link: ShownAccessLink;
    onDismiss: () => void;
}

const AccessLinkDialog = ({link, onDismiss}: Props) =>
{
    const [copied, setCopied] = useState<string | null>(null);

    const copy = async () =>
    {
        try
        {
            await navigator.clipboard.writeText(link.url);
            setCopied("Link copied.");
        }
        catch
        {
            setCopied("Copying failed. Select the link and copy it yourself.");
        }
    };

    return (
        <EditorDialog id={"information-request-access-link-dialog"}
                      title={"Access link"}
                      readOnly={true}
                      onConfirm={onDismiss}
                      onDismiss={onDismiss}>
            <Text id={"information-request-access-link-explanation"}>
                {`Send this link to ${link.partyLabel}. It is shown only once; if it is lost, resend the link to create a new one and end the old one.`}
            </Text>
            <Field id={"information-request-access-link-field"}
                   label={"Link"}>
                <Input id={"information-request-access-link-value"}
                       value={link.url}
                       readOnly={true}
                       onFocus={event => event.target.select()}/>
            </Field>
            <Button id={"information-request-access-link-copy"}
                    appearance={"secondary"}
                    shape={"circular"}
                    icon={<CopyRegular/>}
                    onClick={() => void copy()}>
                Copy link
            </Button>
            <Text id={"information-request-access-link-copied"}
                  role={"status"}
                  aria-live={"polite"}>
                {copied ?? ""}
            </Text>
        </EditorDialog>
    );
};

export default AccessLinkDialog;
