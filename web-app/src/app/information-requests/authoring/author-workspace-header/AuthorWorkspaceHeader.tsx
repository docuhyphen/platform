import {Badge, Button, Title2} from "@fluentui/react-components";
import {ArrowLeftRegular, EyeRegular} from "@fluentui/react-icons";
import {useNavigate} from "react-router-dom";
import {InformationRequestState} from "../../../models/models.tsx";
import {requestStateLabels} from "../../operations/operationsLabels.ts";
import {useAuthorWorkspaceHeaderStyles} from "./AuthorWorkspaceHeaderStyles.tsx";

interface Props
{
    title: string;
    state: InformationRequestState;
    exchangeId: string;
    onPreview: () => void;
}

const AuthorWorkspaceHeader = ({title, state, exchangeId, onPreview}: Props) =>
{
    const styles = useAuthorWorkspaceHeaderStyles();
    const navigate = useNavigate();

    return (
        <header id={"information-request-author-header"}
                className={styles.header}>
            <div id={"information-request-author-title-group"}
                 className={styles.titleGroup}>
                <Button id={"information-request-author-back"}
                        appearance={"subtle"}
                        shape={"circular"}
                        icon={<ArrowLeftRegular/>}
                        onClick={() => navigate(`/exchanges?s=${encodeURIComponent(exchangeId)}`)}>
                    Back to Exchange
                </Button>
                <div id={"information-request-author-title-line"}
                     className={styles.titleLine}>
                    <Title2 id={"information-request-author-title"}
                            as={"h1"}
                            className={styles.title}>
                        {title}
                    </Title2>
                    <Badge id={"information-request-author-state"}
                           appearance={"tint"}
                           shape={"circular"}>
                        {requestStateLabels[state]}
                    </Badge>
                </div>
            </div>
            <div id={"information-request-author-header-actions"}
                 className={styles.actions}>
                <Button id={"information-request-author-preview"}
                        appearance={"secondary"}
                        shape={"circular"}
                        icon={<EyeRegular/>}
                        onClick={onPreview}>
                    Preview as recipient
                </Button>
            </div>
        </header>
    );
};

export default AuthorWorkspaceHeader;
