import {useParams} from "react-router-dom";
import AuthorWorkspaceContent from "../author-workspace-content/AuthorWorkspaceContent.tsx";

const InformationRequestAuthorWorkspace = () =>
{
    const {requestId = ""} = useParams<{ requestId: string }>();

    return (
        <AuthorWorkspaceContent key={requestId}
                                requestId={requestId}/>
    );
};

export default InformationRequestAuthorWorkspace;
